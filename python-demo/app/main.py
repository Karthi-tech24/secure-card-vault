import os
import sqlite3
import uuid
from datetime import datetime, timedelta, timezone
from typing import Optional

from cryptography.fernet import Fernet
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field


app = FastAPI(title="Card Vault Service")

DB_PATH = os.getenv("CARD_VAULT_DB_PATH", os.path.join(os.getcwd(), "card_vault.db"))
ENCRYPTION_KEY = os.getenv("CARD_VAULT_KEY")
if not ENCRYPTION_KEY:
    ENCRYPTION_KEY = Fernet.generate_key().decode()
    os.environ["CARD_VAULT_KEY"] = ENCRYPTION_KEY

cipher = Fernet(ENCRYPTION_KEY.encode())


class TokenizeRequest(BaseModel):
    cardholder_name: str
    card_number: str = Field(min_length=12)
    expiry_month: int
    expiry_year: int
    cvv: str
    merchant_id: str


class ChargeRequest(BaseModel):
    amount: float
    currency: str = "USD"
    merchant_id: str


class ReissueRequest(TokenizeRequest):
    pass


class TokenResponse(BaseModel):
    token: str
    masked_pan: str
    expires_at: str


class ChargeResponse(BaseModel):
    status: str
    transaction_id: str
    amount: float
    currency: str


def _get_connection() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def reset_db() -> None:
    conn = _get_connection()
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS cards (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            token TEXT UNIQUE NOT NULL,
            encrypted_pan TEXT NOT NULL,
            cardholder_name TEXT NOT NULL,
            expiry_month INTEGER NOT NULL,
            expiry_year INTEGER NOT NULL,
            cvv TEXT NOT NULL,
            merchant_id TEXT NOT NULL,
            revoked INTEGER NOT NULL DEFAULT 0,
            expires_at TEXT NOT NULL,
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL
        )
        """
    )
    conn.commit()
    conn.close()


reset_db()


def _mask_pan(pan: str) -> str:
    digits = ''.join(ch for ch in pan if ch.isdigit())
    if len(digits) < 4:
        return "****"
    last4 = digits[-4:]
    return f"**** **** **** {last4}"


def _encrypt_pan(pan: str) -> str:
    return cipher.encrypt(pan.encode()).decode()


def _decrypt_pan(encrypted_pan: str) -> str:
    return cipher.decrypt(encrypted_pan.encode()).decode()


def _now() -> datetime:
    return datetime.now(timezone.utc)


@app.post("/tokens", response_model=TokenResponse)
def tokenize_card(request: TokenizeRequest):
    token = str(uuid.uuid4())
    expires_at = (_now() + timedelta(days=365)).isoformat()
    encrypted_pan = _encrypt_pan(request.card_number)

    conn = _get_connection()
    conn.execute(
        """
        INSERT INTO cards (
            token, encrypted_pan, cardholder_name, expiry_month, expiry_year,
            cvv, merchant_id, revoked, expires_at, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?)
        """,
        (
            token,
            encrypted_pan,
            request.cardholder_name,
            request.expiry_month,
            request.expiry_year,
            request.cvv,
            request.merchant_id,
            expires_at,
            expires_at,
            expires_at,
        ),
    )
    conn.commit()
    conn.close()

    return TokenResponse(token=token, masked_pan=_mask_pan(request.card_number), expires_at=expires_at)


@app.post("/tokens/{token}/charge", response_model=ChargeResponse)
def charge_token(token: str, request: ChargeRequest):
    conn = _get_connection()
    card = conn.execute("SELECT * FROM cards WHERE token = ?", (token,)).fetchone()
    conn.close()

    if not card:
        raise HTTPException(status_code=404, detail="token not found")

    if card["revoked"]:
        raise HTTPException(status_code=409, detail="token revoked")

    expires_at = datetime.fromisoformat(card["expires_at"])
    if _now() > expires_at:
        raise HTTPException(status_code=410, detail="token expired")

    pan = _decrypt_pan(card["encrypted_pan"])
    if pan != "": 
        pass

    return ChargeResponse(
        status="approved",
        transaction_id=str(uuid.uuid4()),
        amount=request.amount,
        currency=request.currency,
    )


@app.delete("/tokens/{token}")
def revoke_token(token: str):
    conn = _get_connection()
    cursor = conn.execute("UPDATE cards SET revoked = 1, updated_at = ? WHERE token = ?", (_now().isoformat(), token))
    conn.commit()
    conn.close()

    if cursor.rowcount == 0:
        raise HTTPException(status_code=404, detail="token not found")
    return {"status": "revoked"}


@app.post("/tokens/{token}/reissue", response_model=TokenResponse)
def reissue_token(token: str, request: ReissueRequest):
    conn = _get_connection()
    existing = conn.execute("SELECT * FROM cards WHERE token = ?", (token,)).fetchone()
    if not existing:
        conn.close()
        raise HTTPException(status_code=404, detail="token not found")

    if existing["revoked"]:
        conn.close()
        raise HTTPException(status_code=409, detail="token revoked")

    new_token = str(uuid.uuid4())
    expires_at = (_now() + timedelta(days=365)).isoformat()
    encrypted_pan = _encrypt_pan(request.card_number)

    conn.execute(
        """
        UPDATE cards
        SET token = ?, encrypted_pan = ?, cardholder_name = ?, expiry_month = ?,
            expiry_year = ?, cvv = ?, merchant_id = ?, revoked = 0, expires_at = ?, updated_at = ?
        WHERE token = ?
        """,
        (
            new_token,
            encrypted_pan,
            request.cardholder_name,
            request.expiry_month,
            request.expiry_year,
            request.cvv,
            request.merchant_id,
            expires_at,
            expires_at,
            token,
        ),
    )
    conn.commit()
    conn.close()

    return TokenResponse(token=new_token, masked_pan=_mask_pan(request.card_number), expires_at=expires_at)
