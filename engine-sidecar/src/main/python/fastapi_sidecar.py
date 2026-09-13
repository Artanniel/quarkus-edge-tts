# FastAPI sidecar for Edge TTS

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import asyncio
import edge_tts

app = FastAPI()

class SynthesizeRequest(BaseModel):
    text: str
    voice: str
    rate: float = 1.0
    pitch: float = 1.0
    volume: float = 1.0

@app.post("/synthesize", response_class="application/octet-stream")
async def synthesize(req: SynthesizeRequest):
    try:
        communicate = edge_tts.Communicate(
            text=req.text,
            voice=req.voice,
            rate=req.rate,
            pitch=req.pitch,
            volume=req.volume,
        )
        # edge_tts returns async generator of audio chunks
        async for chunk in communicate.stream():
            # Yield raw bytes directly – FastAPI will stream them.
            yield chunk
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
