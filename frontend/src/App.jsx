import { useState, useEffect } from 'react'
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts'
import SockJS from 'sockjs-client';
import { Stomp } from '@stomp/stompjs';
import './App.css'

const getDecodedToken = (token) => {
  if (!token) return null;
  try {
    const payload = token.split('.')[1];
    return JSON.parse(atob(payload));
  } catch (e) {
    return null;
  }
};

function App() {
  const [token, setToken] = useState(localStorage.getItem('jwt') || '')

  // Auth Form State
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [email, setEmail] = useState('')
  const [factoryName, setFactoryName] = useState('')
  const [loginFactoryId, setLoginFactoryId] = useState('')

  const [isSignUp, setIsSignUp] = useState(false)
  const [successMsg, setSuccessMsg] = useState('')
  const [error, setError] = useState('')

  // Telemetry & Dashboard State
  const [availableSensors, setAvailableSensors] = useState(['STACK-01'])
  const [sensorId, setSensorId] = useState('STACK-01')
  const [question, setQuestion] = useState(`Analyze ${sensorId} compliance. If violations exist, list strict [MITIGATION ACTIONS].`)
  const [aiResponse, setAiResponse] = useState('')
  const [loading, setLoading] = useState(false)
  const [telemetryData, setTelemetryData] = useState([])

  // FACTORY ADMIN STATE (For creating workers)
  const [newWorkerUser, setNewWorkerUser] = useState('')
  const [newWorkerEmail, setNewWorkerEmail] = useState('')
  const [newWorkerPass, setNewWorkerPass] = useState('')
  const [adminMsg, setAdminMsg] = useState('')

  // Extract User Info from JWT
  const decodedToken = getDecodedToken(token);
  const userRole = decodedToken?.role;
  const userFactoryId = decodedToken?.factoryId;
  const isAdmin = userRole === 'ROLE_ADMIN';

  const handleAuth = async (e) => {
    e.preventDefault()
    setError('')
    setSuccessMsg('')

    if (isSignUp) {
      // MANAGER SIGNUP
      if (!username || !email || !password || !factoryName) {
        setError('All fields (Username, Email, Password, Factory Name) are required.');
        return;
      }
      try {
        const res = await fetch('http://localhost:8080/api/auth/signup', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username, email, password, factoryName })
        })
        const text = await res.text();
        if (!res.ok) throw new Error(text || 'Signup failed');

        setSuccessMsg(text);
        setIsSignUp(false);
        setUsername(''); setPassword(''); setEmail(''); setFactoryName('');
      } catch (err) { setError(err.message) }
    } else {
      // LOGIN (ADMIN or WORKER)
      if (!username || !password || !loginFactoryId) {
        setError('Username, Password, and Factory ID are required.');
        return;
      }
      try {
        const res = await fetch('http://localhost:8080/api/auth/login', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username, password, factoryId: parseInt(loginFactoryId) })
        })
        if (!res.ok) throw new Error(await res.text() || 'Login failed');
        const jwt = await res.text();
        setToken(jwt);
        localStorage.setItem('jwt', jwt);
      } catch (err) { setError(err.message) }
    }
  }

  const handleLogout = () => {
    setToken('')
    localStorage.removeItem('jwt')
    setAiResponse('')
    setTelemetryData([])
    setAdminMsg('')
  }

  const handleCreateWorker = async (e) => {
    e.preventDefault();
    setAdminMsg('');
    try {
      const response = await fetch("http://localhost:8080/api/admin/create-worker", {
        method: "POST",
        headers: { "Content-Type": "application/json", "Authorization": `Bearer ${token}` },
        body: JSON.stringify({ username: newWorkerUser, password: newWorkerPass, email: newWorkerEmail })
      });

      const text = await response.text();
      if (response.ok) {
        setAdminMsg(`✅ ${text}`);
        setNewWorkerUser(""); setNewWorkerPass(""); setNewWorkerEmail("");
      } else {
        setAdminMsg(`❌ Error: ${text}`);
      }
    } catch (error) { setAdminMsg(`❌ Network Error: ${error.message}`); }
  };

  useEffect(() => {
    if (!token) return;
    fetch('http://localhost:8080/api/telemetry/sensors', { headers: { 'Authorization': `Bearer ${token}` } })
      .then(res => res.json())
      .then(data => {
        if (data && data.length > 0) {
          setAvailableSensors(data);
          if (!data.includes(sensorId)) setSensorId(data[0]);
        }
      }).catch(err => console.error("Failed to fetch sensors:", err));
  }, [token]);

  useEffect(() => {
    if (!token) return;

    fetch(`http://localhost:8080/api/telemetry/history/${encodeURIComponent(sensorId)}`, {
      headers: { 'Authorization': `Bearer ${token}` }
    })
      .then(res => res.ok ? res.json() : [])
      .then(data => setTelemetryData(Array.isArray(data) ? data : []))
      .catch(err => console.error("Initial telemetry load failed:", err));

    const socket = new SockJS('http://localhost:8080/ws-enviroguard');
    const stompClient = Stomp.over(socket);
    stompClient.debug = () => {};

    let isActive = true;

    stompClient.connect({ Authorization: `Bearer ${token}` }, () => {
      if (!isActive) { stompClient.disconnect(); return; }

      const targetFactory = userFactoryId || 1;

      stompClient.subscribe(`/topic/telemetry/${targetFactory}`, (message) => {
        try {
          const newLog = JSON.parse(message.body);
          if (newLog.type === 'CRON_AUDIT_REPORT') {
            if (!newLog.sensorId || newLog.sensorId === sensorId) setAiResponse(newLog.report || '');
            return;
          }
          if (newLog.sensorId === sensorId) setTelemetryData(prev => [...prev, newLog].slice(-15));
        } catch (err) { console.error('Parse error:', err); }
      });
    }, (error) => { if (isActive) console.error('STOMP error:', error); });

    return () => {
      isActive = false;
      if (stompClient.connected) stompClient.disconnect(() => socket.close());
      else socket.close();
    };
  }, [token, sensorId, userFactoryId]);


const runAudit = async (e) => {
  e.preventDefault();

  // 1. Guard check: Ensure inputs are non-empty before calling backend
  if (!question?.trim() || !sensorId?.trim()) {
    setError('Please select a valid sensor and enter an audit query.');
    return;
  }

  setLoading(true);
  setError('');
  setAiResponse('');

  try {
    const queryUrl = `http://localhost:8080/api/audit/query?question=${encodeURIComponent(question)}&sensorId=${encodeURIComponent(sensorId)}`;

    const res = await fetch(queryUrl, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    });

    // 2. Handle session expiration or authority issues (ROLE_ADMIN required)
    if (res.status === 403) {
      handleLogout();
      throw new Error('Session expired or unauthorized. Please log in again.');
    }

    if (!res.ok) {
      const errText = await res.text();
      throw new Error(errText || `Server responded with status ${res.status}`);
    }

    // 3. Set plain text response from backend AI model
    const reportText = await res.text();
    setAiResponse(reportText);

  } catch (err) {
    // 4. Distinguish between connection refusal vs API errors
    if (err.name === 'TypeError' && err.message === 'Failed to fetch') {
      setError('Unable to connect to backend server at http://localhost:8080.');
    } else {
      setError(err.message || 'An unexpected error occurred during the audit.');
    }
  } finally {
    setLoading(false);
  }
};
  useEffect(() => {
    setQuestion(`Analyze ${sensorId} compliance. If violations exist, list strict [MITIGATION ACTIONS].`)
  }, [sensorId])

  // --- LOGIN & SIGNUP SCREEN ---
  if (!token) {
    return (
      <div className="login-wrapper">
        <div className="login-card">
          <div className="brand-badge">ENVIROGUARD ENTERPRISE</div>
          <h2>{isSignUp ? 'Register Factory & Admin' : 'Sign In to Portal'}</h2>

          {error && <div className="alert-box error" style={{ background: '#fee2e2', color: '#991b1b', padding: '0.75rem', borderRadius: '6px', marginBottom: '1rem' }}>{error}</div>}
          {successMsg && (
            <div className="alert-box success" style={{ background: '#dcfce7', color: '#15803d', padding: '0.75rem', borderRadius: '6px', marginBottom: '1rem' }}>
              {successMsg}
            </div>
          )}

          <form onSubmit={handleAuth}>
            <div className="input-group">
              <label>Username</label>
              <input type="text" value={username} onChange={e => setUsername(e.target.value)} required />
            </div>

            {isSignUp ? (
              <>
                <div className="input-group">
                  <label>Email Address</label>
                  <input type="email" value={email} onChange={e => setEmail(e.target.value)} placeholder="manager@factory.com" required />
                </div>
                <div className="input-group">
                  <label>Factory Name</label>
                  <input type="text" value={factoryName} onChange={e => setFactoryName(e.target.value)} placeholder="e.g. Austin Plant A" required />
                </div>
              </>
            ) : (
              <div className="input-group">
                <label>Factory ID</label>
                <input type="number" value={loginFactoryId} onChange={e => setLoginFactoryId(e.target.value)} placeholder="e.g. 1" required />
              </div>
            )}

            <div className="input-group">
              <label>Password</label>
              <input type="password" value={password} onChange={e => setPassword(e.target.value)} required />
            </div>

            <button type="submit" className="primary-btn full">
              {isSignUp ? 'Register Factory' : 'Authenticate'}
            </button>
          </form>

          <div style={{ marginTop: '1.25rem', textAlign: 'center', fontSize: '0.9rem', color: '#64748b' }}>
            {isSignUp ? (
              <span>
                Already registered?{' '}
                <button
                  type="button"
                  onClick={() => { setIsSignUp(false); setError(''); setSuccessMsg(''); }}
                  style={{ background: 'none', border: 'none', color: '#0284c7', cursor: 'pointer', fontWeight: 'bold', textDecoration: 'underline', padding: 0 }}
                >
                  Sign In here
                </button>
              </span>
            ) : (
              <span>
                Factory Manager?{' '}
                <button
                  type="button"
                  onClick={() => { setIsSignUp(true); setError(''); setSuccessMsg(''); }}
                  style={{ background: 'none', border: 'none', color: '#0284c7', cursor: 'pointer', fontWeight: 'bold', textDecoration: 'underline', padding: 0 }}
                >
                  Register new factory
                </button>
              </span>
            )}
          </div>
        </div>
      </div>
    )
  }

const downloadCSV = () => {
  if (!telemetryData || telemetryData.length === 0) {
    alert("No telemetry data available to export.");
    return;
  }

  // Define CSV headers
  const headers = ["Timestamp", "Sensor ID", "NOx (ppm)", "CO (ppm)", "SO2 (ppm)"];

  // Format data rows
  const rows = telemetryData.map(row => [
    `"${new Date(row.timestamp).toLocaleString()}"`,
    `"${row.sensorId || sensorId}"`,
    row.noxLevel ?? '',
    row.coLevel ?? '',
    row.so2Level ?? ''
  ]);

  // Combine headers and rows
  const csvContent = "data:text/csv;charset=utf-8,"
    + [headers.join(","), ...rows.map(e => e.join(","))].join("\n");

  // Create download link and trigger click
  const encodedUri = encodeURI(csvContent);
  const link = document.createElement("a");
  link.setAttribute("href", encodedUri);
  link.setAttribute("download", `telemetry_${sensorId}_${new Date().toISOString().slice(0,10)}.csv`);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
};

  // --- FACTORY DASHBOARD ---
  return (
    <div className="dashboard-container">
      <header className="navbar">
        <div className="nav-brand">
          <span className="logo-icon">🌱</span>
          <div>
            <h1>EnviroGuard AI</h1>
            <span className="nav-subtitle">Factory ID: {userFactoryId || 'Unknown'}</span>
          </div>
        </div>
        <div className="user-nav">
          <span className="status-indicator online">System Operational</span>
          <span style={{ marginLeft: '10px', fontSize: '0.85rem', color: '#64748b', fontWeight: 'bold' }}>({isAdmin ? 'ADMIN' : 'WORKER'})</span>
          <button onClick={handleLogout} className="logout-btn" style={{ marginLeft: '15px' }}>Log Out</button>
        </div>
      </header>

      <main className="dashboard-content">
        {isAdmin && (
          <section className="grid-layout" style={{ gridTemplateColumns: '1fr 1fr', marginBottom: '1.5rem' }}>

            <div className="card control-panel">
              <div className="card-header">
                <h3>Audit Control Panel</h3>
                <span className="badge info" style={{ background: '#fee2e2', color: '#991b1b' }}>Admin Only</span>
              </div>
              <form onSubmit={runAudit}>
                <div className="input-group">
                  <label>Target Sensor Unit</label>
                  <select value={sensorId} onChange={e => setSensorId(e.target.value)} style={{ padding: '0.65rem', borderRadius: '6px', border: '1px solid #cbd5e1', width: '100%' }}>
                    {availableSensors.map(s => <option key={s} value={s}>{s}</option>)}
                  </select>
                </div>
                <div className="input-group">
                  <label>Audit Query</label>
                  <input type="text" value={question} onChange={e => setQuestion(e.target.value)} />
                </div>
                <button type="submit" className="primary-btn" disabled={loading}>
                  {loading ? <span className="spinner-text">Analyzing...</span> : 'Execute Compliance Audit'}
                </button>
              </form>
            </div>

            {/* Create Worker Panel */}
            <div className="card control-panel">
              <div className="card-header">
                <h3>Create Factory Worker</h3>
                <span className="badge info" style={{ background: '#dbeafe', color: '#1e40af' }}>Admin Only</span>
              </div>
              {adminMsg && (
                <div style={{ marginBottom: '1rem', padding: '0.5rem', borderRadius: '4px', fontSize: '0.9rem', fontWeight: 'bold', background: adminMsg.includes('✅') ? '#dcfce7' : '#fee2e2', color: adminMsg.includes('✅') ? '#15803d' : '#991b1b' }}>
                  {adminMsg}
                </div>
              )}
              <form onSubmit={handleCreateWorker}>
                <div className="input-group">
                  <label>Worker Username</label>
                  <input type="text" value={newWorkerUser} onChange={e => setNewWorkerUser(e.target.value)} required placeholder="worker_john" />
                </div>
                <div className="input-group">
                  <label>Worker Email</label>
                  <input type="email" value={newWorkerEmail} onChange={e => setNewWorkerEmail(e.target.value)} required placeholder="john@factory.com" />
                </div>
                <div className="input-group">
                  <label>Worker Password</label>
                  <input type="password" value={newWorkerPass} onChange={e => setNewWorkerPass(e.target.value)} required />
                </div>
                <button type="submit" className="primary-btn full" style={{ marginTop: '0.5rem' }}>
                  Add Worker to Factory {userFactoryId}
                </button>
              </form>
            </div>

          </section>
        )}

        <section className="grid-layout">
          <div className="card stats-panel full-width-stats">
            <div className="card-header">
              <h3>Sensor Quick Status</h3>
            </div>
            <div className="metrics-grid">
              <div className="metric-box">
                <span className="metric-label">SO2 Reading</span>
                <span className="metric-value warning">{telemetryData.length > 0 ? telemetryData[telemetryData.length - 1].so2Level : 'N/A'}</span>
                <span className="metric-sub">Latest Read</span>
              </div>
              <div className="metric-box">
                <span className="metric-label">NOx Level</span>
                <span className="metric-value safe">{telemetryData.length > 0 ? telemetryData[telemetryData.length - 1].noxLevel : '--'} <small>ppm</small></span>
                <span className="metric-sub">Limit: 15.0 ppm</span>
              </div>
              <div className="metric-box">
                <span className="metric-label">CO Level</span>
                <span className="metric-value safe">{telemetryData.length > 0 ? telemetryData[telemetryData.length - 1].coLevel : '--'} <small>ppm</small></span>
                <span className="metric-sub">Limit: 10.0 ppm</span>
              </div>
            </div>
          </div>
        </section>

        <section className="card chart-panel" style={{ marginBottom: '1.5rem' }}>
          <div className="card-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '15px' }}>
              <h3>Live Emissions Telemetry Feed</h3>
              <select value={sensorId} onChange={e => setSensorId(e.target.value)} style={{ padding: '0.25rem 0.5rem', borderRadius: '4px', border: '1px solid #cbd5e1' }}>
                {availableSensors.map(s => <option key={s} value={s}>{s}</option>)}
              </select>
            </div>
            {/* ADD THIS BUTTON */}
                <button
                  onClick={downloadCSV}
                  className="logout-btn"
                  style={{ background: '#0284c7', color: '#fff', border: 'none', fontWeight: 'bold' }}
                >
                  📥 Download CSV
                </button>
          </div>
          <div style={{ width: '100%', height: '320px', marginTop: '1rem' }}>
            <ResponsiveContainer>
              <LineChart data={telemetryData} margin={{ top: 5, right: 20, bottom: 5, left: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" vertical={false} />
                <XAxis dataKey="timestamp" stroke="#64748b" fontSize={12} tickFormatter={(tick) => tick ? new Date(tick).toLocaleTimeString() : ''} />
                <YAxis stroke="#64748b" fontSize={12} />
                <Tooltip labelFormatter={(label) => new Date(label).toLocaleString()} contentStyle={{ borderRadius: '8px' }} />
                <Legend wrapperStyle={{ paddingTop: '15px' }} />
                <Line type="monotone" dataKey="noxLevel" stroke="#16a34a" name="NOx (ppm)" strokeWidth={2.5} dot={{ r: 4 }} activeDot={{ r: 6 }} />
                <Line type="monotone" dataKey="coLevel" stroke="#0284c7" name="CO (ppm)" strokeWidth={2.5} dot={{ r: 4 }} activeDot={{ r: 6 }} />
                <Line type="monotone" dataKey="so2Level" stroke="#d97706" name="SO2 (ppm)" strokeWidth={2.5} dot={{ r: 4 }} activeDot={{ r: 6 }} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </section>

        {aiResponse && (
          <section className="card report-card">
            <div className="card-header">
              <h3>Official AI Audit Report ({sensorId})</h3>
              <span className="badge success">Verified Analysis</span>
            </div>
            <div className="report-body">
              <pre className="ai-formatted-text">{aiResponse}</pre>
            </div>
          </section>
        )}

      </main>
    </div>
  );
}

export default App;