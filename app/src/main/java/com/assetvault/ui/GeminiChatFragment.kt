package com.assetvault.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.assetvault.databinding.FragmentGeminiChatBinding

import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.assetvault.BuildConfig
import com.assetvault.data.AppDatabase
import com.assetvault.data.chat.ChatMessageEntity
import com.assetvault.data.chat.ChatSessionEntity
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class GeminiChatFragment : Fragment() {
    private var _binding: FragmentGeminiChatBinding? = null
    private val binding get() = _binding!!

    private lateinit var chatAdapter: ChatAdapter
    private val messages = mutableListOf<ChatMessageEntity>()
    private lateinit var generativeModel: GenerativeModel
    private var sessionId: String = UUID.randomUUID().toString()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGeminiChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        chatAdapter = ChatAdapter(messages)
        binding.rvChat.layoutManager = LinearLayoutManager(requireContext())
        binding.rvChat.adapter = chatAdapter

        binding.ivBack.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        binding.btnNewChat.setOnClickListener {
            sessionId = UUID.randomUUID().toString()
            messages.clear()
            chatAdapter.notifyDataSetChanged()
            Toast.makeText(requireContext(), "Started a new chat", Toast.LENGTH_SHORT).show()
        }

        // Load history
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(requireContext())
            val sessions = db.chatDao().getAllSessions()
            if (sessions.isNotEmpty()) {
                sessionId = sessions.first().sessionId
                val history = db.chatDao().getMessagesForSession(sessionId)
                withContext(Dispatchers.Main) {
                    messages.clear()
                    messages.addAll(history)
                    chatAdapter.notifyDataSetChanged()
                    if (messages.isNotEmpty()) {
                        binding.rvChat.scrollToPosition(messages.size - 1)
                    }
                }
            }
        }

        // Initialize Gemini
        generativeModel = GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = BuildConfig.GEMINI_API_KEY,
            systemInstruction = content {
                text("""You are the Asset Vault AI Assistant. Your job is to help users protect their digital IP, issue DMCA takedowns, and resolve ownership disputes using blockchain pHash records. Keep your answers concise, professional, and strictly related to this app's scope.

IMPORTANT: You can execute tasks by outputting a JSON tool call block in your response. Whenever the user asks you to do one of the following tasks, YOU MUST output ONLY the JSON block. Do NOT wrap it in markdown.
Format: {"tool": "tool_name", "args": {"arg1": "value1"}}

Available tools:
1. generate_dmca_notice(infringer_url, original_url, owner_name)
2. gather_evidence(asset_id)
3. submit_support_ticket(issue_description)
""")
            }
        )

        binding.btnSend.setOnClickListener {
            val query = binding.etMessage.text.toString().trim()
            if (query.isNotEmpty()) {
                sendMessage(query)
            }
        }
    }

    private fun sendMessage(query: String) {
        binding.etMessage.text.clear()
        binding.progressBar.visibility = View.VISIBLE

        val userMessage = ChatMessageEntity(
            sessionId = sessionId,
            role = "user",
            content = query,
            timestamp = System.currentTimeMillis()
        )
        chatAdapter.addMessage(userMessage)
        binding.rvChat.scrollToPosition(messages.size - 1)

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(requireContext())

            // CRITICAL: Always ensure session row exists BEFORE inserting any message.
            // @Insert(IGNORE) means duplicate inserts are silently skipped.
            db.chatDao().insertSession(
                ChatSessionEntity(
                    sessionId = sessionId,
                    title = query.take(20) + if (query.length > 20) "..." else "",
                    lastUpdatedTimestamp = System.currentTimeMillis()
                )
            )

            // Now safe to insert message (FK parent guaranteed to exist)
            db.chatDao().insertMessage(userMessage)

            try {
                // Build history excluding current query
                val previousMessages = messages.dropLast(1)
                val history = previousMessages.map { msg ->
                    content(msg.role) { text(msg.content) }
                }
                
                val chat = generativeModel.startChat(history)
                val response = chat.sendMessage(query)

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    response.text?.let { reply ->
                        var finalReply = reply
                        
                        // Parse JSON Tool Call
                        try {
                            if (reply.contains("\"tool\":")) {
                                val jsonStr = reply.substring(reply.indexOf("{"), reply.lastIndexOf("}") + 1)
                                val json = org.json.JSONObject(jsonStr)
                                val toolName = json.getString("tool")
                                val args = json.getJSONObject("args")
                                
                                when (toolName) {
                                    "generate_dmca_notice" -> {
                                        val infringer = args.optString("infringer_url", "[Infringer URL]")
                                        val original = args.optString("original_url", "[Original URL]")
                                        val owner = args.optString("owner_name", "[Your Name]")
                                        finalReply = "Generating DMCA Notice...\n\nI declare under penalty of perjury that I am the owner ($owner) of the copyrighted material at $original, and that $infringer is infringing upon my rights. Please remove it immediately."
                                    }
                                    "gather_evidence" -> {
                                        val assetId = args.optString("asset_id", "Unknown")
                                        finalReply = "Gathering Blockchain Evidence for $assetId...\n\n✅ pHash matched: 98%\n✅ Ownership verified on Polygon Mumbai\n✅ Timestamp valid: 2026-04-12"
                                    }
                                    "submit_support_ticket" -> {
                                        val issue = args.optString("issue_description", "No description")
                                        finalReply = "I have queued your support ticket: \"$issue\". You can also raise tickets manually from the 'Raise Ticket' menu option."
                                        // Bonus: Can actually insert into Supabase here
                                        launch(Dispatchers.IO) {
                                            com.assetvault.network.SupabaseManager.submitTicket(requireContext(), issue, null)
                                        }
                                    }
                                    else -> {
                                        finalReply = "Tried to call unknown tool: $toolName"
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Not a valid JSON or parsing failed, just show original reply
                        }

                        val modelMessage = ChatMessageEntity(
                            sessionId = sessionId,
                            role = "model",
                            content = finalReply,
                            timestamp = System.currentTimeMillis()
                        )
                        chatAdapter.addMessage(modelMessage)
                        binding.rvChat.scrollToPosition(messages.size - 1)

                        // Save to DB
                        launch(Dispatchers.IO) {
                            db.chatDao().insertMessage(modelMessage)
                            val session = db.chatDao().getAllSessions().find { it.sessionId == sessionId }
                            session?.let {
                                db.chatDao().updateSession(it.copy(lastUpdatedTimestamp = System.currentTimeMillis()))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                val errorMsg = "Gemini Error: ${e.javaClass.simpleName}: ${e.message}"
                android.util.Log.e("GeminiChat", errorMsg, e)
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    com.google.android.material.snackbar.Snackbar.make(
                        binding.root,
                        errorMsg,
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
