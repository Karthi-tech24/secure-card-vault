import os
import sqlite3

import pytest
from fastapi.testclient import TestClient

from app.main import app, reset_db


@pytest.fixture(autouse=True)
def isolated_db(tmp_path, monkeypatch):
    db_path = tmp_path / "vault.db"
    monkeypatch.setenv("CARD_VAULT_DB_PATH", str(db_path))
    reset_db()
    yield db_path
    reset_db()


client = TestClient(app)


def test_tokenize_and_charge_without_persisting_plain_pan(isolated_db):
    response = client.post(
        "/tokens",
        json={
            "cardholder_name": "Jane Doe",
            "card_number": "4111111111111111",
            "expiry_month": 12,
            "expiry_year": 2030,
            "cvv": "123",
            "merchant_id": "merchant-001",
        },
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["token"]
    assert payload["masked_pan"] == "**** **** **** 1111"

    conn = sqlite3.connect(isolated_db)
    row = conn.execute("SELECT encrypted_pan FROM cards WHERE token = ?", (payload["token"],)).fetchone()
    conn.close()

    assert row is not None
    assert "4111111111111111" not in row[0]

    charge_response = client.post(
        f"/tokens/{payload['token']}/charge",
        json={"amount": 19.99, "currency": "USD", "merchant_id": "merchant-001"},
    )

    assert charge_response.status_code == 200
    assert charge_response.json()["status"] == "approved"


def test_revoke_and_reissue_token(isolated_db):
    response = client.post(
        "/tokens",
        json={
            "cardholder_name": "John Smith",
            "card_number": "5555555555554444",
            "expiry_month": 8,
            "expiry_year": 2029,
            "cvv": "999",
            "merchant_id": "merchant-002",
        },
    )
    token = response.json()["token"]

    revoke_response = client.delete(f"/tokens/{token}")
    assert revoke_response.status_code == 200

    charge_response = client.post(
        f"/tokens/{token}/charge",
        json={"amount": 5.0, "currency": "USD", "merchant_id": "merchant-002"},
    )
    assert charge_response.status_code == 409

    reissue_response = client.post(
        f"/tokens/{token}/reissue",
        json={
            "cardholder_name": "John Smith",
            "card_number": "5105105105105100",
            "expiry_month": 10,
            "expiry_year": 2031,
            "cvv": "321",
            "merchant_id": "merchant-002",
        },
    )

    assert reissue_response.status_code == 200
    reissued = reissue_response.json()
    assert reissued["token"] != token
    assert reissued["masked_pan"] == "**** **** **** 5100"
