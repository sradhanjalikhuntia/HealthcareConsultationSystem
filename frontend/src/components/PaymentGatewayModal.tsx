import React, { useState, useEffect } from 'react';
import { billingApi, type PaymentReceiptResponse } from '../api/billing';

interface PaymentGatewayModalProps {
  bill: {
    id: string;
    totalAmount: number;
    uniquePatientId?: string;
    patientId: string;
    patientName?: string;
    createdAt?: string;
  };
  isPatient?: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

export const PaymentGatewayModal: React.FC<PaymentGatewayModalProps> = ({
  bill,
  isPatient,
  onClose,
  onSuccess,
}) => {
  const [tab, setTab] = useState<'UPI' | 'CARD' | 'NET_BANKING' | 'CASH'>('UPI');
  const [submitting, setSubmitting] = useState(false);
  const [processStep, setProcessStep] = useState<string | null>(null);
  const [receipt, setReceipt] = useState<PaymentReceiptResponse | null>(null);
  const [errorMessage, setErrorMessage] = useState('');

  // UPI State
  const [upiId, setUpiId] = useState('');
  const [upiVerified, setUpiVerified] = useState(false);
  const [qrSeconds, setQrSeconds] = useState(299); // 5 mins countdown

  // Card State
  const [cardNumber, setCardNumber] = useState('');
  const [cardHolder, setCardHolder] = useState(bill.patientName || 'ApexCare Valued Patient');
  const [cardExpiry, setCardExpiry] = useState('');
  const [cardCvv, setCardCvv] = useState('');

  // Net Banking State
  const [selectedBank, setSelectedBank] = useState('HDFC');

  // Countdown for UPI QR
  useEffect(() => {
    if (tab !== 'UPI' || receipt) return;
    const interval = setInterval(() => {
      setQrSeconds(prev => (prev > 0 ? prev - 1 : 0));
    }, 1000);
    return () => clearInterval(interval);
  }, [tab, receipt]);

  const formatTimer = (secs: number) => {
    const m = Math.floor(secs / 60);
    const s = secs % 60;
    return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
  };

  const handleFormatCard = (val: string) => {
    const raw = val.replace(/\D/g, '').substring(0, 16);
    const parts = raw.match(/.{1,4}/g);
    setCardNumber(parts ? parts.join(' ') : raw);
  };

  const handleFormatExpiry = (val: string) => {
    const raw = val.replace(/\D/g, '').substring(0, 4);
    if (raw.length >= 2) {
      setCardExpiry(`${raw.substring(0, 2)}/${raw.substring(2)}`);
    } else {
      setCardExpiry(raw);
    }
  };

  const executePayment = async (method: string, customRef?: string) => {
    setSubmitting(true);
    setErrorMessage('');
    
    // Simulate Realistic Gateway Handshake steps
    setProcessStep('Connecting to Secure Banking Gateway (256-bit SSL)...');
    await new Promise(r => setTimeout(r, 600));

    setProcessStep(`Authorizing ${method} payment of $${Number(bill.totalAmount).toFixed(2)}...`);
    await new Promise(r => setTimeout(r, 700));

    try {
      const generatedRef = customRef || `TXN-${method}-${Date.now().toString().slice(-6)}`;
      const res = await billingApi.processPayment(bill.id, {
        paymentMethod: method,
        amount: Number(bill.totalAmount),
        transactionReference: generatedRef,
        notes: `Simulated online payment via ApexCare Gateway (${method})`
      });

      setProcessStep('Payment Settled & Receipt Generated ✓');
      await new Promise(r => setTimeout(r, 500));

      setReceipt(res.data);
      onSuccess();
    } catch (err: any) {
      setProcessStep(null);
      setErrorMessage(err.response?.data?.message || 'Payment transaction failed. Please retry.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="modal-overlay" onClick={e => e.target === e.currentTarget && !submitting && onClose()}>
      <div className="modal modal-lg" style={{ maxWidth: 680, overflow: 'hidden' }}>
        
        {/* Header */}
        <div className="modal-header" style={{ background: 'linear-gradient(135deg, rgba(99, 102, 241, 0.15) 0%, rgba(16, 185, 129, 0.1) 100%)' }}>
          <div>
            <div className="flex items-center gap-2">
              <span style={{ fontSize: '1.25rem' }}>🔒</span>
              <span className="modal-title" style={{ fontSize: '1.15rem' }}>ApexCare Secure Payment Terminal</span>
            </div>
            <span className="text-xs text-muted">PCI-DSS Level 1 · End-to-End Encrypted Settlement</span>
          </div>
          <button className="modal-close" onClick={onClose} disabled={submitting}>×</button>
        </div>

        {/* Modal Body */}
        <div className="modal-body" style={{ padding: '1.5rem' }}>
          
          {/* Bill Summary Banner */}
          <div style={{
            background: 'var(--surface-solid)',
            border: '1px solid var(--border)',
            borderRadius: 'var(--radius)',
            padding: '1rem 1.25rem',
            marginBottom: '1.25rem',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: '0.75rem'
          }}>
            <div>
              <span className="text-xs text-muted">INVOICE #{bill.id.slice(0, 8).toUpperCase()}</span>
              <div style={{ fontWeight: 700, fontSize: '1rem', marginTop: '0.15rem' }}>
                {bill.patientName || 'Registered Patient'}
              </div>
            </div>
            <div style={{ textAlign: 'right' }}>
              <span className="text-xs text-muted">TOTAL AMOUNT DUE</span>
              <div style={{ fontSize: '1.65rem', fontWeight: 800, color: 'var(--success)', letterSpacing: '-0.5px' }}>
                ${Number(bill.totalAmount).toFixed(2)}
              </div>
            </div>
          </div>

          {errorMessage && (
            <div className="alert alert-error mb-3">
              <span>⚠️</span>
              <span>{errorMessage}</span>
            </div>
          )}

          {/* Success Receipt View */}
          {receipt ? (
            <div className="fade-in" style={{ textAlign: 'center', padding: '1rem 0' }}>
              <div style={{
                width: 64,
                height: 64,
                borderRadius: '50%',
                background: 'rgba(16, 185, 129, 0.2)',
                color: 'var(--success)',
                fontSize: '2rem',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                margin: '0 auto 1rem auto',
                border: '2px solid var(--success)'
              }}>
                ✓
              </div>
              <h3 style={{ fontSize: '1.35rem', fontWeight: 800, marginBottom: '0.25rem' }}>Payment Successful!</h3>
              <p className="text-muted text-sm mb-4">
                Transaction reference: <code style={{ color: 'var(--primary-light)', fontWeight: 700 }}>{receipt.transactionReference || receipt.receiptId || 'TXN-CONFIRMED'}</code>
              </p>

              <div style={{
                background: 'rgba(255, 255, 255, 0.03)',
                border: '1px solid var(--border)',
                borderRadius: 'var(--radius)',
                padding: '1.25rem',
                maxWidth: 420,
                margin: '0 auto 1.5rem auto',
                textAlign: 'left'
              }}>
                <div className="flex justify-between py-1 text-sm">
                  <span className="text-muted">Settled Amount:</span>
                  <span style={{ fontWeight: 700, color: 'var(--success)' }}>${Number(receipt.amountPaid || bill.totalAmount).toFixed(2)}</span>
                </div>
                <div className="flex justify-between py-1 text-sm">
                  <span className="text-muted">Method:</span>
                  <span style={{ fontWeight: 600 }}>{receipt.paymentMethod || tab}</span>
                </div>
                <div className="flex justify-between py-1 text-sm">
                  <span className="text-muted">Payment Date:</span>
                  <span>{new Date().toLocaleString()}</span>
                </div>
                <div className="flex justify-between py-1 text-sm">
                  <span className="text-muted">Status:</span>
                  <span className="badge badge-success">PAID & CLEARED</span>
                </div>
              </div>

              <div className="flex justify-center gap-2">
                <button type="button" className="btn btn-secondary" onClick={() => window.print()}>
                  <span>🖨️</span>
                  <span>Print Receipt</span>
                </button>
                <button type="button" className="btn btn-primary" onClick={onClose}>
                  <span>Done</span>
                </button>
              </div>
            </div>
          ) : submitting ? (
            /* Animated Progress State */
            <div style={{ textAlign: 'center', padding: '3rem 1rem' }}>
              <div className="spinner mb-3" style={{ width: 44, height: 44, margin: '0 auto' }} />
              <h4 style={{ fontSize: '1.1rem', fontWeight: 700, marginBottom: '0.5rem' }}>Processing Payment...</h4>
              <p className="text-sm text-muted" style={{ minHeight: '1.5rem', color: 'var(--accent)' }}>
                {processStep}
              </p>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-3)', marginTop: '1rem' }}>
                Please do not refresh or close the browser window.
              </div>
            </div>
          ) : (
            /* Gateway Tabs & Details */
            <div>
              {/* Payment Methods Bar */}
              <div className="tab-pills-bar mb-4">
                <button
                  type="button"
                  className={`tab-pill ${tab === 'UPI' ? 'active' : ''}`}
                  onClick={() => setTab('UPI')}
                >
                  <span>📱</span>
                  <span>UPI / QR Code</span>
                </button>
                <button
                  type="button"
                  className={`tab-pill ${tab === 'CARD' ? 'active' : ''}`}
                  onClick={() => setTab('CARD')}
                >
                  <span>💳</span>
                  <span>Credit / Debit Card</span>
                </button>
                <button
                  type="button"
                  className={`tab-pill ${tab === 'NET_BANKING' ? 'active' : ''}`}
                  onClick={() => setTab('NET_BANKING')}
                >
                  <span>🏦</span>
                  <span>Net Banking</span>
                </button>
                {!isPatient && (
                  <button
                    type="button"
                    className={`tab-pill ${tab === 'CASH' ? 'active' : ''}`}
                    onClick={() => setTab('CASH')}
                  >
                    <span>💵</span>
                    <span>Cash / Desk</span>
                  </button>
                )}
              </div>

              {/* ── TAB 1: UPI / QR CODE ── */}
              {tab === 'UPI' && (
                <div className="fade-in">
                  <div style={{
                    display: 'grid',
                    gridTemplateColumns: 'minmax(200px, 220px) 1fr',
                    gap: '1.5rem',
                    alignItems: 'center'
                  }}>
                    {/* Dynamic QR Code Box */}
                    <div style={{
                      background: '#ffffff',
                      borderRadius: 'var(--radius)',
                      padding: '1rem',
                      textAlign: 'center',
                      boxShadow: '0 8px 24px rgba(0,0,0,0.3)',
                      border: '2px solid #e2e8f0'
                    }}>
                      <div style={{
                        position: 'relative',
                        width: '180px',
                        height: '180px',
                        margin: '0 auto',
                        background: '#ffffff',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center'
                      }}>
                        {/* High-fidelity Vector QR Code Representation */}
                        <svg viewBox="0 0 100 100" width="100%" height="100%">
                          <rect width="100" height="100" fill="#ffffff" />
                          {/* Corner Squares */}
                          <rect x="5" y="5" width="26" height="26" fill="#0f172a" rx="2" />
                          <rect x="9" y="9" width="18" height="18" fill="#ffffff" />
                          <rect x="13" y="13" width="10" height="10" fill="#0f172a" rx="1" />

                          <rect x="69" y="5" width="26" height="26" fill="#0f172a" rx="2" />
                          <rect x="73" y="9" width="18" height="18" fill="#ffffff" />
                          <rect x="77" y="13" width="10" height="10" fill="#0f172a" rx="1" />

                          <rect x="5" y="69" width="26" height="26" fill="#0f172a" rx="2" />
                          <rect x="9" y="73" width="18" height="18" fill="#ffffff" />
                          <rect x="13" y="77" width="10" height="10" fill="#0f172a" rx="1" />

                          {/* Data Matrix Dots */}
                          <rect x="36" y="8" width="6" height="6" fill="#0f172a" />
                          <rect x="46" y="8" width="8" height="6" fill="#0f172a" />
                          <rect x="58" y="8" width="5" height="6" fill="#0f172a" />
                          <rect x="36" y="18" width="8" height="6" fill="#0f172a" />
                          <rect x="50" y="18" width="12" height="6" fill="#0f172a" />

                          <rect x="8" y="36" width="6" height="8" fill="#0f172a" />
                          <rect x="20" y="36" width="12" height="6" fill="#0f172a" />
                          <rect x="36" y="36" width="28" height="28" fill="#4338ca" rx="4" />
                          <circle cx="50" cy="50" r="8" fill="#ffffff" />
                          <text x="50" y="53" fontSize="8" fontWeight="bold" textAnchor="middle" fill="#4338ca">₹</text>

                          <rect x="70" y="36" width="14" height="6" fill="#0f172a" />
                          <rect x="88" y="36" width="6" height="16" fill="#0f172a" />
                          <rect x="70" y="48" width="12" height="8" fill="#0f172a" />

                          <rect x="36" y="70" width="8" height="10" fill="#0f172a" />
                          <rect x="50" y="70" width="14" height="6" fill="#0f172a" />
                          <rect x="70" y="70" width="8" height="8" fill="#0f172a" />
                          <rect x="82" y="70" width="12" height="12" fill="#0f172a" />
                          <rect x="36" y="86" width="28" height="8" fill="#0f172a" />
                          <rect x="70" y="84" width="8" height="10" fill="#0f172a" />
                        </svg>
                      </div>
                      
                      <div style={{ marginTop: '0.5rem', color: '#0f172a', fontSize: '0.75rem', fontWeight: 700 }}>
                        Scan & Pay via any UPI App
                      </div>
                      <div style={{ fontSize: '0.7rem', color: '#64748b', marginTop: '0.15rem' }}>
                        Expires in: <strong style={{ color: qrSeconds < 60 ? '#ef4444' : '#0284c7' }}>{formatTimer(qrSeconds)}</strong>
                      </div>
                    </div>

                    {/* UPI App Icons & Instant Simulator */}
                    <div>
                      <div className="text-xs text-muted mb-2 font-bold uppercase tracking-wide">
                        Supported Instant Apps
                      </div>
                      <div className="flex gap-2 mb-3 flex-wrap">
                        {['GPay', 'PhonePe', 'Paytm', 'BHIM UPI', 'Cred'].map(app => (
                          <div
                            key={app}
                            style={{
                              padding: '0.4rem 0.75rem',
                              background: 'var(--surface-solid)',
                              border: '1px solid var(--border)',
                              borderRadius: 'var(--radius-sm)',
                              fontSize: '0.8rem',
                              fontWeight: 600,
                              display: 'flex',
                              alignItems: 'center',
                              gap: '0.35rem'
                            }}
                          >
                            <span>⚡</span>
                            <span>{app}</span>
                          </div>
                        ))}
                      </div>

                      {/* Custom UPI ID */}
                      <div className="form-group mb-3">
                        <label className="form-label" style={{ fontSize: '0.8rem' }}>Or Enter Your UPI ID</label>
                        <div style={{ display: 'flex', gap: '0.5rem' }}>
                          <input
                            type="text"
                            className="form-input"
                            style={{ padding: '0.5rem 0.75rem', fontSize: '0.85rem' }}
                            placeholder="e.g. mobile@upi or name@okaxis"
                            value={upiId}
                            onChange={e => {
                              setUpiId(e.target.value);
                              setUpiVerified(false);
                            }}
                          />
                          <button
                            type="button"
                            className="btn btn-secondary btn-sm"
                            onClick={() => upiId && setUpiVerified(true)}
                          >
                            {upiVerified ? '✓ Valid' : 'Verify'}
                          </button>
                        </div>
                      </div>

                      {/* Simulate Instant Action */}
                      <button
                        type="button"
                        className="btn btn-primary btn-block"
                        onClick={() => executePayment('UPI', `UPI-REF-${Math.floor(Math.random() * 900000 + 100000)}`)}
                      >
                        <span>⚡</span>
                        <span>Simulate Instant UPI Approval (${Number(bill.totalAmount).toFixed(2)})</span>
                      </button>
                    </div>
                  </div>
                </div>
              )}

              {/* ── TAB 2: CREDIT / DEBIT CARD ── */}
              {tab === 'CARD' && (
                <div className="fade-in">
                  {/* Visual 3D Virtual Card */}
                  <div style={{
                    background: 'linear-gradient(135deg, #1e1b4b 0%, #312e81 50%, #4338ca 100%)',
                    borderRadius: '16px',
                    padding: '1.25rem 1.5rem',
                    color: '#ffffff',
                    maxWidth: 380,
                    margin: '0 auto 1.25rem auto',
                    boxShadow: '0 12px 30px rgba(0,0,0,0.4)',
                    border: '1px solid rgba(255,255,255,0.15)',
                    position: 'relative'
                  }}>
                    <div className="flex justify-between items-center mb-4">
                      <span style={{ fontSize: '0.75rem', fontWeight: 700, letterSpacing: '1px', opacity: 0.8 }}>
                        APEXCARE HEALTH CARD
                      </span>
                      <span style={{ fontSize: '1.1rem', fontWeight: 900, fontStyle: 'italic' }}>
                        VISA
                      </span>
                    </div>

                    <div style={{
                      width: 36,
                      height: 26,
                      borderRadius: 4,
                      background: 'linear-gradient(135deg, #f59e0b 0%, #d97706 100%)',
                      marginBottom: '1rem'
                    }} />

                    <div style={{
                      fontSize: '1.2rem',
                      letterSpacing: '2px',
                      fontFamily: 'monospace',
                      fontWeight: 700,
                      marginBottom: '1rem'
                    }}>
                      {cardNumber || '•••• •••• •••• 4242'}
                    </div>

                    <div className="flex justify-between items-end">
                      <div>
                        <div style={{ fontSize: '0.65rem', textTransform: 'uppercase', opacity: 0.7 }}>Card Holder</div>
                        <div style={{ fontSize: '0.85rem', fontWeight: 600 }}>{cardHolder}</div>
                      </div>
                      <div>
                        <div style={{ fontSize: '0.65rem', textTransform: 'uppercase', opacity: 0.7 }}>Expires</div>
                        <div style={{ fontSize: '0.85rem', fontWeight: 600 }}>{cardExpiry || '12/28'}</div>
                      </div>
                    </div>
                  </div>

                  {/* Card Form Inputs */}
                  <div className="form-group mb-2">
                    <label className="form-label" style={{ fontSize: '0.8rem' }}>Card Number</label>
                    <input
                      type="text"
                      className="form-input"
                      placeholder="4242 •••• •••• 4242"
                      value={cardNumber}
                      onChange={e => handleFormatCard(e.target.value)}
                      maxLength={19}
                    />
                  </div>

                  <div className="form-row mb-2">
                    <div className="form-group">
                      <label className="form-label" style={{ fontSize: '0.8rem' }}>Cardholder Name</label>
                      <input
                        type="text"
                        className="form-input"
                        placeholder="Name on card"
                        value={cardHolder}
                        onChange={e => setCardHolder(e.target.value)}
                      />
                    </div>
                    <div className="form-group">
                      <label className="form-label" style={{ fontSize: '0.8rem' }}>Expiry (MM/YY)</label>
                      <input
                        type="text"
                        className="form-input"
                        placeholder="MM/YY"
                        value={cardExpiry}
                        onChange={e => handleFormatExpiry(e.target.value)}
                        maxLength={5}
                      />
                    </div>
                    <div className="form-group" style={{ maxWidth: 100 }}>
                      <label className="form-label" style={{ fontSize: '0.8rem' }}>CVV</label>
                      <input
                        type="password"
                        className="form-input"
                        placeholder="•••"
                        value={cardCvv}
                        onChange={e => setCardCvv(e.target.value.replace(/\D/g, '').substring(0, 4))}
                        maxLength={4}
                      />
                    </div>
                  </div>

                  <button
                    type="button"
                    className="btn btn-primary btn-block mt-3"
                    onClick={() => executePayment('CARD', `CARD-AUTH-${Date.now().toString().slice(-6)}`)}
                  >
                    <span>🔒</span>
                    <span>Pay ${Number(bill.totalAmount).toFixed(2)} with Card</span>
                  </button>
                </div>
              )}

              {/* ── TAB 3: NET BANKING ── */}
              {tab === 'NET_BANKING' && (
                <div className="fade-in">
                  <div className="text-xs text-muted mb-2 font-bold uppercase tracking-wide">
                    Select Your Bank
                  </div>
                  <div className="grid-3 gap-2 mb-4">
                    {[
                      { code: 'HDFC', name: 'HDFC Bank', icon: '🏛️' },
                      { code: 'SBI', name: 'State Bank of India', icon: '🔵' },
                      { code: 'ICICI', name: 'ICICI Bank', icon: '🟠' },
                      { code: 'AXIS', name: 'Axis Bank', icon: '🟣' },
                      { code: 'KOTAK', name: 'Kotak Bank', icon: '🔴' },
                      { code: 'PNB', name: 'Punjab National Bank', icon: '🟡' },
                    ].map(bank => (
                      <button
                        key={bank.code}
                        type="button"
                        className="btn"
                        style={{
                          background: selectedBank === bank.code ? 'rgba(99, 102, 241, 0.2)' : 'var(--surface-solid)',
                          border: `1px solid ${selectedBank === bank.code ? 'var(--primary)' : 'var(--border)'}`,
                          padding: '0.75rem',
                          display: 'flex',
                          alignItems: 'center',
                          gap: '0.5rem',
                          justifyContent: 'flex-start',
                          borderRadius: 'var(--radius-sm)'
                        }}
                        onClick={() => setSelectedBank(bank.code)}
                      >
                        <span>{bank.icon}</span>
                        <span style={{ fontSize: '0.8rem', fontWeight: 600 }}>{bank.name}</span>
                      </button>
                    ))}
                  </div>

                  <button
                    type="button"
                    className="btn btn-primary btn-block"
                    onClick={() => executePayment('NET_BANKING', `NET-${selectedBank}-${Date.now().toString().slice(-6)}`)}
                  >
                    <span>Proceed to {selectedBank} NetBanking Gateway →</span>
                  </button>
                </div>
              )}

              {/* ── TAB 4: CASH / DESK (Staff only) ── */}
              {tab === 'CASH' && !isPatient && (
                <div className="fade-in" style={{ padding: '1rem 0' }}>
                  <div style={{
                    padding: '1.25rem',
                    background: 'rgba(16, 185, 129, 0.08)',
                    border: '1px solid rgba(16, 185, 129, 0.2)',
                    borderRadius: 'var(--radius)',
                    marginBottom: '1.25rem'
                  }}>
                    <div style={{ fontWeight: 700, marginBottom: '0.35rem', color: 'var(--success)' }}>
                      💵 Cash Payment at Front Desk
                    </div>
                    <p className="text-sm text-muted" style={{ margin: 0 }}>
                      Collect physical cash from the patient at the reception or cashier counter. Confirm the exact total amount of <strong>${Number(bill.totalAmount).toFixed(2)}</strong> before acknowledging settlement.
                    </p>
                  </div>

                  <button
                    type="button"
                    className="btn btn-success btn-block"
                    onClick={() => executePayment('CASH', `CASH-RCPT-${Date.now().toString().slice(-6)}`)}
                  >
                    <span>✓ Confirm Cash Collection & Settle Bill</span>
                  </button>
                </div>
              )}
            </div>
          )}
        </div>

        {/* Modal Footer */}
        {!receipt && !submitting && (
          <div className="modal-footer" style={{ borderTop: '1px solid var(--border)' }}>
            <button type="button" className="btn btn-secondary" onClick={onClose}>
              Cancel
            </button>
          </div>
        )}
      </div>
    </div>
  );
};
