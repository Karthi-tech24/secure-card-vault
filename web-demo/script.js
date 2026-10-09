const homeCard = document.getElementById('homeCard');
const saveCardPage = document.getElementById('saveCardPage');
const successPage = document.getElementById('successPage');
const payPage = document.getElementById('payPage');
const paymentSuccessPage = document.getElementById('paymentSuccessPage');
const historyPage = document.getElementById('historyPage');
const saveCardForm = document.getElementById('saveCardForm');
const payForm = document.getElementById('payForm');
const continueBtn = document.getElementById('continueBtn');
const homeFromPaymentBtn = document.getElementById('homeFromPaymentBtn');
const homeFromHistoryBtn = document.getElementById('homeFromHistoryBtn');
const generatedToken = document.getElementById('generatedToken');
const transactionId = document.getElementById('transactionId');
const saveMessage = document.getElementById('saveMessage');
const successMessage = document.getElementById('successMessage');
const apiBaseUrl = 'http://localhost:8080';

function setFormMessage(element, message, type = 'error') {
  if (!element) {
    return;
  }

  element.textContent = message || '';
  element.className = `status-message ${type}`;
}

function showPage(page) {
  homeCard.classList.add('hidden');
  saveCardPage.classList.add('hidden');
  successPage.classList.add('hidden');
  payPage.classList.add('hidden');
  paymentSuccessPage.classList.add('hidden');
  historyPage.classList.add('hidden');

  if (page === 'save') {
    saveCardPage.classList.remove('hidden');
  } else if (page === 'success') {
    successPage.classList.remove('hidden');
  } else if (page === 'pay') {
    payPage.classList.remove('hidden');
  } else if (page === 'payment-success') {
    paymentSuccessPage.classList.remove('hidden');
  } else if (page === 'history') {
    historyPage.classList.remove('hidden');
  } else {
    homeCard.classList.remove('hidden');
  }
}

async function loadTransactions() {
  try {
    const response = await fetch(`${apiBaseUrl}/transactions`, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json'
      }
    });

    const data = await response.json();
    if (!response.ok || !data.success) {
      throw new Error(data.message || 'Unable to load transactions');
    }

    const tbody = document.querySelector('#historyPage tbody');
    tbody.innerHTML = '';

    if (!data.transactions || data.transactions.length === 0) {
      tbody.innerHTML = '<tr><td colspan="4">No transactions found</td></tr>';
      return;
    }

    data.transactions.forEach((transaction) => {
      const row = document.createElement('tr');
      row.innerHTML = `
        <td>${transaction.token || ''}</td>
        <td>$${Number(transaction.amount || 0).toFixed(2)}</td>
        <td>${transaction.status || ''}</td>
        <td>${transaction.date ? transaction.date.split('T')[0] : ''}</td>`;
      tbody.appendChild(row);
    });
  } catch (error) {
    alert(error.message);
  }
}

document.querySelectorAll('.menu-btn').forEach((button) => {
  button.addEventListener('click', () => {
    if (button.dataset.page === 'save') {
      showPage('save');
    } else if (button.dataset.page === 'pay') {
      showPage('pay');
    } else if (button.dataset.page === 'history') {
      showPage('history');
      loadTransactions();
    }
  });
});

saveCardForm.addEventListener('submit', async (event) => {
  event.preventDefault();

  const payload = {
    holderName: document.getElementById('holderName').value.trim(),
    cardNumber: document.getElementById('cardNumber').value.replace(/\s+/g, ''),
    expiryDate: document.getElementById('expiryDate').value.trim(),
    cvv: document.getElementById('cvv').value.trim()
  };

  if (!payload.holderName || !payload.cardNumber || !payload.expiryDate || !payload.cvv) {
    setFormMessage(saveMessage, 'Please complete every field before saving.', 'error');
    return;
  }

  if (!/^\d{16}$/.test(payload.cardNumber)) {
    setFormMessage(saveMessage, 'Card number must contain exactly 16 digits.', 'error');
    return;
  }

  if (!/^(0[1-9]|1[0-2])\/\d{2}$/.test(payload.expiryDate)) {
    setFormMessage(saveMessage, 'Expiry date must be in MM/YY format.', 'error');
    return;
  }

  if (!/^\d{3}$/.test(payload.cvv)) {
    setFormMessage(saveMessage, 'CVV must contain exactly 3 digits.', 'error');
    return;
  }

  setFormMessage(saveMessage, 'Saving card...', 'info');

  try {
    const response = await fetch(`${apiBaseUrl}/saveCard`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(payload)
    });

    const data = await response.json().catch(() => ({}));
    if (!response.ok || !data.success) {
      throw new Error(data.message || 'Unable to save card');
    }

    generatedToken.textContent = data.token || 'N/A';
    if (successMessage) {
      successMessage.textContent = data.message || 'Card saved successfully';
    }
    setFormMessage(saveMessage, data.message || 'Card saved successfully', 'success');
    showPage('success');
  } catch (error) {
    setFormMessage(saveMessage, error.message || 'Unable to save card', 'error');
  }
});

payForm.addEventListener('submit', async (event) => {
  event.preventDefault();

  try {
    const payload = {
      token: document.getElementById('savedToken').value.trim(),
      amount: document.getElementById('amount').value
    };

    const response = await fetch(`${apiBaseUrl}/payment`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(payload)
    });

    const data = await response.json();
    if (!response.ok || !data.success) {
      throw new Error(data.message || 'Unable to process payment');
    }

    transactionId.textContent = data.transactionId || 'N/A';
    showPage('payment-success');
  } catch (error) {
    alert(error.message);
  }
});

continueBtn.addEventListener('click', () => {
  showPage('home');
  saveCardForm.reset();
  setFormMessage(saveMessage, '', 'error');
  if (successMessage) {
    successMessage.textContent = 'Card saved successfully';
  }
});

homeFromPaymentBtn.addEventListener('click', () => {
  showPage('home');
  payForm.reset();
});

homeFromHistoryBtn.addEventListener('click', () => {
  showPage('home');
});
