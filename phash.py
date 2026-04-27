import os
import json
import functions_framework
from google.cloud import aiplatform
from google.cloud.aiplatform.matching_engine.matching_engine_index_endpoint import (
    MatchingEngineIndexEndpoint,
)
from supabase import create_client, Client
import numpy as np

# --- Config from Environment Variables ---
PROJECT_ID = os.environ.get("PROJECT_ID", "")
REGION = os.environ.get("REGION", "asia-south1")
INDEX_ENDPOINT_ID = os.environ.get("INDEX_ENDPOINT_ID", "")
DEPLOYED_INDEX_ID = os.environ.get("DEPLOYED_INDEX_ID", "")
SUPABASE_URL = os.environ.get("SUPABASE_URL", "")       #received from environment variable
SUPABASE_KEY = os.environ.get("SUPABASE_KEY", "")       #received from environment variable
SIMILARITY_THRESHOLD = 0.85

_supabase = None
_endpoint = None

def get_supabase():
    global _supabase
    if _supabase is None:
        _supabase = create_client(SUPABASE_URL, SUPABASE_KEY)
    return _supabase

def get_endpoint():
    global _endpoint
    if _endpoint is None:
        aiplatform.init(project=PROJECT_ID, location=REGION)
        _endpoint = MatchingEngineIndexEndpoint(
            index_endpoint_name=f"projects/{PROJECT_ID}/locations/{REGION}/indexEndpoints/{INDEX_ENDPOINT_ID}"
        )
    return _endpoint

def phash_hex_to_vector(phash_hex: str) -> list:
    try:
        hash_int = int(phash_hex, 16)
        binary_str = format(hash_int, '064b')
        return [float(bit) for bit in binary_str]
    except Exception:
        padded = phash_hex.ljust(64, '0')[:64]
        return [float(int(c, 16)) / 15.0 for c in padded]

def compute_similarity(v1: list, v2: list) -> float:
    a = np.array(v1)
    b = np.array(v2)
    norm = np.linalg.norm(a) * np.linalg.norm(b)
    if norm == 0:
        return 0.0
    return float(np.dot(a, b) / norm)

def cors_headers():
    return {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
        "Access-Control-Allow-Headers": "Content-Type",
        "Content-Type": "application/json"
    }

@functions_framework.http
def phash_api(request):
    if request.method == "OPTIONS":
        return ("", 204, cors_headers())

    path = request.path.rstrip("/")
    headers = cors_headers()

    if path == "/health" or path == "":
        return (json.dumps({"status": "ok"}), 200, headers)

    elif path == "/search":
        if request.method != "POST":
            return (json.dumps({"error": "POST required"}), 405, headers)
        data = request.get_json(silent=True) or {}
        phash_hex = data.get("hash", "")
        device_hash = data.get("device_hash", "unknown")
        location_name = data.get("location_name", "Unknown")
        lat = data.get("lat", 0.0)
        lng = data.get("lng", 0.0)
        if not phash_hex:
            return (json.dumps({"error": "hash is required"}), 400, headers)
        query_vector = phash_hex_to_vector(phash_hex)
        try:
            endpoint = get_endpoint()
            response = endpoint.find_neighbors(
                deployed_index_id=DEPLOYED_INDEX_ID,
                queries=[query_vector],
                num_neighbors=1,
            )
            neighbors = response[0] if response else []
        except Exception as e:
            return (json.dumps({"error": f"Vector search failed: {str(e)}"}), 500, headers)
        if not neighbors:
            return (json.dumps({"match_found": False}), 200, headers)
        best_match = neighbors[0]
        matched_hash = best_match.id.split("::")[0]
        similarity = compute_similarity(query_vector, phash_hex_to_vector(matched_hash))
        if similarity < SIMILARITY_THRESHOLD:
            return (json.dumps({"match_found": False}), 200, headers)
        try:
            supabase = get_supabase()
            # FIX: Avoid NoneType crashes
            asset = supabase.table("assets").select("blockchain_tx").eq("hash", matched_hash).execute()
            if asset and hasattr(asset, 'data') and len(asset.data) > 0:
                blockchain_tx = asset.data[0].get("blockchain_tx", "")
            else:
                blockchain_tx = ""
        except Exception:
            blockchain_tx = ""
        try:
            supabase = get_supabase()
            sighting = supabase.table("sightings").insert({
                "matched_phash": matched_hash,
                "sighting_phash": phash_hex,
                "similarity_score": round(similarity, 4),
                "device_hash": device_hash,
                "location_name": location_name,
                "location_lat": lat,
                "location_lng": lng,
                "blockchain_owner_tx": blockchain_tx,
            }).execute()
            sighting_id = sighting.data[0]["id"] if sighting.data else None
        except Exception:
            sighting_id = None
        return (json.dumps({
            "match_found": True,
            "similarity": round(similarity * 100, 1),
            "matched_hash": matched_hash,
            "owner_blockchain_tx": blockchain_tx,
            "sighting_id": sighting_id,
        }), 200, headers)

    elif path == "/register":
        if request.method != "POST":
            return (json.dumps({"error": "POST required"}), 405, headers)
        data = request.get_json(silent=True) or {}
        phash_hex = data.get("hash", "")
        user_id = data.get("user_id", "")
        blockchain_tx = data.get("blockchain_tx", "")
        if not phash_hex or not user_id:
            return (json.dumps({"error": "hash and user_id are required"}), 400, headers)
        query_vector = phash_hex_to_vector(phash_hex)
        try:
            endpoint = get_endpoint()
            index_resource_name = None
            for d in endpoint.deployed_indexes:
                if d.id == DEPLOYED_INDEX_ID:
                    index_resource_name = d.index
                    break
            
            if not index_resource_name:
                raise Exception(f"Deployed index {DEPLOYED_INDEX_ID} not found on endpoint")
                
            my_index = aiplatform.MatchingEngineIndex(index_name=index_resource_name)
            
            my_index.upsert_datapoints(
                datapoints=[{
                    "datapoint_id": f"{phash_hex}::{user_id}",
                    "feature_vector": query_vector,
                }]
            )
        except Exception as e:
            return (json.dumps({"error": f"Vector insert failed: {str(e)}"}), 500, headers)
        try:
            supabase = get_supabase()
            asset = supabase.table("assets").insert({
                "hash": phash_hex,
                "app_email": user_id, 
                "blockchain_tx": blockchain_tx,
            }).execute()
            asset_id = asset.data[0]["id"] if asset.data else None
        except Exception as e:
            return (json.dumps({"error": f"Supabase insert failed: {str(e)}"}), 500, headers)
        return (json.dumps({"registered": True, "asset_id": asset_id}), 200, headers)

    elif path == "/sightings":
        phash_hex = request.args.get("hash", "")
        if not phash_hex:
            return (json.dumps({"error": "hash query param required"}), 400, headers)
        query_vector = phash_hex_to_vector(phash_hex)
        try:
            endpoint = get_endpoint()
            response = endpoint.find_neighbors(
                deployed_index_id=DEPLOYED_INDEX_ID,
                queries=[query_vector],
                num_neighbors=1,
            )
            neighbors = response[0] if response else []
        except Exception as e:
            return (json.dumps({"error": f"Vector search failed: {str(e)}"}), 500, headers)
        if not neighbors:
            return (json.dumps({"sightings": [], "total": 0}), 200, headers)
        matched_hash = neighbors[0].id.split("::")[0]
        try:
            supabase = get_supabase()
            sightings = supabase.table("sightings").select("*").eq("matched_phash", matched_hash).order("detected_at", desc=True).execute()
            return (json.dumps({
                "sightings": sightings.data,
                "total": len(sightings.data),
                "matched_hash": matched_hash,
            }), 200, headers)
        except Exception as e:
            return (json.dumps({"error": f"Supabase query failed: {str(e)}"}), 500, headers)
    
    elif path == "/protect":
        if request.method != "POST":
            return (json.dumps({"error": "POST required"}), 405, headers)
        data = request.get_json(silent=True) or {}
        phash_val = data.get("hash", "")
        user_id = data.get("user_id", "")
        if not phash_val or not user_id:
            return (json.dumps({"error": "hash and user_id required"}), 400, headers)
        try:
            supabase = get_supabase()
            # FIX: Check app_email instead of user_id to prevent UUID crashes
            result = supabase.table("assets") \
                .update({"is_enforced": True, "enforced_at": "now()"}) \
                .eq("hash", phash_val) \
                .eq("app_email", user_id) \
                .execute()
                
            if not result or not hasattr(result, 'data') or len(result.data) == 0:
                return (json.dumps({"error": "Asset not found or unauthorized"}), 404, headers)
                
            return (json.dumps({
                "success": True,
                "protected_at": result.data[0].get("enforced_at", None)
            }), 200, headers)
        except Exception as e:
            return (json.dumps({"error": str(e)}), 500, headers)

    elif path == "/enforcement":
        phash_val = request.args.get("hash", "")
        if not phash_val:
            return (json.dumps({"error": "hash required"}), 400, headers)
        try:
            supabase = get_supabase()
            # FIX: Fetch the blockchain_tx and use safe .execute()
            result = supabase.table("assets") \
                .select("is_enforced, enforced_at, app_email, blockchain_tx") \
                .eq("hash", phash_val) \
                .execute()
                
            if not result or not hasattr(result, 'data') or len(result.data) == 0:
                return (json.dumps({"is_enforced": False}), 200, headers)
                
            asset_data = result.data[0]
            
            return (json.dumps({
                "is_enforced": asset_data.get("is_enforced", False),
                "enforced_at": asset_data.get("enforced_at", None),
                "user_id": asset_data.get("app_email", ""),
                "blockchain_tx": asset_data.get("blockchain_tx", "")
            }), 200, headers)
        except Exception as e:
            return (json.dumps({"error": str(e)}), 500, headers)

    elif path == "/fund-wallet":
        if request.method != "POST":
            return (json.dumps({"error": "POST required"}), 405, headers)
        data = request.get_json(silent=True) or {}
        wallet_address = data.get("wallet_address", "")
        app_email = data.get("app_email", "")
        
        if not wallet_address or not app_email:
            return (json.dumps({"error": "wallet_address and app_email required"}), 400, headers)
            
        try:
            # 1. Connect to Web3
            from web3 import Web3
            w3 = Web3(Web3.HTTPProvider("https://rpc-amoy.polygon.technology"))
            
            # --- SECURITY CHECK 1: DOES THE WALLET REALLY NEED FUNDS? ---
            # If the user already has more than 0.05 POL, reject the faucet request.
            # This prevents them from hoarding POL.
            user_balance_wei = w3.eth.get_balance(wallet_address)
            if user_balance_wei > w3.to_wei(0.05, 'ether'):
                return (json.dumps({"error": "Wallet already has sufficient funds."}), 429, headers)
                
            # --- SECURITY CHECK 2: STRICT 24-HOUR LIMIT VIA SUPABASE ---
            supabase = get_supabase()
            record = supabase.table("faucet_requests").select("last_funded").eq("app_email", app_email).execute()
            
            if record and hasattr(record, 'data') and len(record.data) > 0:
                last_str = record.data[0].get("last_funded")
                if last_str:
                    from datetime import datetime, timezone
                    # Safely parse Supabase's ISO string
                    last_funded = datetime.fromisoformat(last_str.replace('Z', '+00:00'))
                    now = datetime.now(timezone.utc)
                    
                    # 86400 seconds = 24 hours
                    if (now - last_funded).total_seconds() < 86400:
                        return (json.dumps({"error": "You can only request funds once per 24 hours."}), 429, headers)
            # 2. Get the Master Wallet
            master_private_key = os.environ.get("MASTER_PRIVATE_KEY")
            if not master_private_key:
                 return (json.dumps({"error": "Faucet not configured"}), 500, headers)
                 
            master_account = w3.eth.account.from_key(master_private_key)
            
            # 3. Build the transaction (sending exactly 0.2 MATIC to the user)
            nonce = w3.eth.get_transaction_count(master_account.address)
            tx = {
                'nonce': nonce,
                'to': wallet_address,
                'value': w3.to_wei(0.2, 'ether'),
                'gas': 21000,
                'maxFeePerGas': w3.to_wei(35, 'gwei'),
                'maxPriorityFeePerGas': w3.to_wei(35, 'gwei'),
                'chainId': 80002 # Polygon Amoy Testnet ID
            }
            
            # 4. Sign and send the transaction
            signed_tx = w3.eth.account.sign_transaction(tx, master_private_key)
            tx_hash = w3.eth.send_raw_transaction(signed_tx.rawTransaction)
            
            # 5. Record the successful refill in Supabase so they are locked out for 24 hours
            from datetime import datetime, timezone
            supabase.table("faucet_requests").upsert({
                "app_email": app_email,
                "last_funded": datetime.now(timezone.utc).isoformat()
            }).execute()
            
            return (json.dumps({"success": True, "tx_hash": tx_hash.hex()}), 200, headers)
        except Exception as e:
            return (json.dumps({"error": str(e)}), 500, headers)

    
    else:
        return (json.dumps({"error": "Not found"}), 404, headers)
