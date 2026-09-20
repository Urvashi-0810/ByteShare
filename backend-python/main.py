from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List, Optional
import firebase_admin
from firebase_admin import credentials, firestore

app = FastAPI()

# Initialize Firebase (requires serviceAccountKey.json)
# cred = credentials.Certificate("serviceAccountKey.json")
# firebase_admin.initialize_app(cred)
# db = firestore.client()

class AppUsage(BaseModel):
    appName: String
    category: String
    minutes: float

class UsageSyncBody(BaseModel):
    userId: String
    reportDate: String
    apps: List[AppUsage]
    source: String = "android"

@app.get("/")
def read_root():
    return {"message": "ByteShare Python Backend Active"}

@app.post("/usage/sync")
async def sync_usage(body: UsageSyncBody):
    # Logic to store in Firestore
    # doc_ref = db.collection("usage").document(f"{body.userId}_{body.reportDate}")
    # doc_ref.set(body.dict())
    return {"status": "success", "userId": body.userId}

@app.get("/group/{group_id}/ranks")
async def get_group_ranks(group_id: String):
    # Logic to fetch group members' usage and calculate ranks using FameEngine logic
    return {"groupId": group_id, "ranks": []}
