import { useState, useEffect } from 'react'
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts'
import SockJS from 'sockjs-client';
import { Stomp } from '@stomp/stompjs';
import { jwtDecode } from 'jwt-decode';
import './App.css'

const getRoleFromToken = (token) => {
  if (!token) return null;
  try {
    const payload = token.split('.')[1];
    const decoded = JSON.parse(atob(payload));
    return decoded.role;
  } catch (e) {
    return null;
  }
};

function App() {
  const [token, setToken] = useState(localStorage.getItem('jwt') || '')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [isSignUp, setIsSignUp] = useState(false)
  const [successMsg, setSuccessMsg] = useState('')

  const [availableSensors, setAvailableSensors] = useState(['STACK-01'])
  const [sensorId, setSensorId] = useState('STACK-01')

  // UPDATED: Forced the AI to provide Mitigation Steps
  const [question, setQuestion] = useState(`Analyze ${sensorId} compliance. If violations exist, list strict [MITIGATION ACTIONS].`)
  const [aiResponse, setAiResponse] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const [telemetryData, setTelemetryData] = useState([])

  const userRole = getRoleFromToken(token);
  const isAdmin = userRole === 'ROLE_ADMIN';
const [liveData, setLiveData] = useState([]);
  const handleAuth = async (e) => {
    e.preventDefault()
    setError('')
    setSuccessMsg('')
    const endpoint = isSignUp ? '/signup' : '/login'
    try {
      const res = await fetch(`http://localhost:8080/api/auth${endpoint}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
      })
      if (!res.ok) throw new Error(await res.text() || 'Authentication failed')
      if (isSignUp) {
        setSuccessMsg('Account created! You can now log in.')
        setIsSignUp(false)
      } else {
        const jwt = await res.text()
        setToken(jwt)
        localStorage.setItem('jwt', jwt)
      }
    } catch (err) { setError(err.message) }
  }

  const handleLogout = () => {
    setToken('')
    localStorage.removeItem('jwt')
    setAiResponse('')
    setTelemetryData([])
  }

  // EXPORT FEATURE: Download CSV
  const downloadCSV = () => {
    if (telemetryData.length === 0) return;
    const headers = ['Timestamp', 'Sensor ID', 'SO2 (ppm)', 'NOx (ppm)', 'CO (ppm)'];
    const rows = telemetryData.map(d => [
      new Date(d.timestamp).toLocaleString(),
      d.sensorId,
      d.so2Level || 0,
      d.noxLevel || 0,
      d.coLevel || 0
    ]);
    const csvContent = "data:text/csv;charset=utf-8,"
      + headers.join(",") + "\n"
      + rows.map(e => e.join(",")).join("\n");
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement("a");
    link.setAttribute("href", encodedUri);
    link.setAttribute("download", `${sensorId}_Telemetry_Report.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  // EXPORT FEATURE: Print / PDF
  const downloadPDF = () => {
    window.print();
  };

  useEffect(() => {
    if (!token) return;
    fetch('http://localhost:8080/api/telemetry/sensors', {
      headers: { 'Authorization': `Bearer ${token}` }
    })
      .then(res => res.json())
      .then(data => {
        if (data && data.length > 0) {
          setAvailableSensors(data);
          if (!data.includes(sensorId)) setSensorId(data[0]);
        }
      })
      .catch(err => console.error("Failed to fetch sensors:", err));
  }, [token]);

  useEffect(() => {
    if (!token) return;

    // Load existing telemetry history for the selected sensor.
    fetch(`http://localhost:8080/api/telemetry/history?sensorId=${encodeURIComponent(sensorId)}`, {
      headers: { 'Authorization': `Bearer ${token}` }
    })
      .then(res => {
        if (!res.ok) throw new Error(`History request failed: ${res.status}`);
        return res.json();
      })
      .then(data => setTelemetryData(Array.isArray(data) ? data : []))
      .catch(err => console.error("Initial telemetry load failed:", err));

    // Subscribe to live telemetry using the SockJS + STOMP endpoint.
    // Change this if your backend assigns factories dynamically.
    const factoryId = 1;
    const socket = new SockJS('http://localhost:8080/ws-enviroguard');
    const stompClient = Stomp.over(socket);

    // Disable verbose STOMP debug logging.
    stompClient.debug = () => {};

    let isActive = true;

    stompClient.connect(
      { Authorization: `Bearer ${token}` },
      () => {
        if (!isActive) {
          stompClient.disconnect();
          return;
        }

        console.log('Connected to STOMP broker');

        stompClient.subscribe(`/topic/telemetry/${factoryId}`, (message) => {
          try {
            const newLog = JSON.parse(message.body);

            if (newLog.type === 'CRON_AUDIT_REPORT') {
              if (!newLog.sensorId || newLog.sensorId === sensorId) {
                setAiResponse(newLog.report || '');
              }
              return;
            }

            // Keep the dashboard chart focused on the currently selected sensor.
            if (newLog.sensorId === sensorId) {
              setTelemetryData(prevData => [...prevData, newLog].slice(-15));
            }

            // Retain the latest received record in liveData if needed elsewhere.
            setLiveData(prevData => [...prevData, newLog].slice(-15));
          } catch (err) {
            console.error('Could not parse STOMP telemetry message:', err);
          }
        });
      },
      (error) => {
        if (isActive) {
          console.error('STOMP WebSocket connection error:', error);
        }
      }
    );

    return () => {
      isActive = false;
      if (stompClient.connected) {
        stompClient.disconnect(() => socket.close());
      } else {
        socket.close();
      }
    };
  }, [token, sensorId]);

  const runAudit = async (e) => {
    e.preventDefault()
    setLoading(true)
    setError('')
    setAiResponse('')
    try {
      const res = await fetch(`http://localhost:8080/api/audit/query?question=${encodeURIComponent(question)}&sensorId=${encodeURIComponent(sensorId)}`, {
        method: 'GET',
        headers: { 'Authorization': `Bearer ${token}` }
      })
      if (res.status === 403) {
        handleLogout()
        throw new Error('Session expired or unauthorized.')
      }
      if (!res.ok) throw new Error('Failed to fetch AI audit')
      setAiResponse(await res.text())
    } catch (err) { setError(err.message) } finally { setLoading(false) }
  }

  useEffect(() => {
    setQuestion(`Analyze ${sensorId} compliance. If violations exist, list strict [MITIGATION ACTIONS].`)
  }, [sensorId])

  if (!token) {
    return (
      <div className="login-wrapper">
        <div className="login-card">
          <div className="brand-badge">ENVIROGUARD ENTERPRISE</div>
          <h2>{isSignUp ? 'Create an Account' : 'Sign In to Portal'}</h2>
          {error && <div className="alert-box error">{error}</div>}
          {successMsg && <div className="alert-box success" style={{background: '#dcfce7', color: '#15803d', padding: '0.75rem', borderRadius: '6px', marginBottom: '1rem'}}>{successMsg}</div>}
          <form onSubmit={handleAuth}>
            <div className="input-group"><label>Username</label><input type="text" value={username} onChange={e => setUsername(e.target.value)} required /></div>
            <div className="input-group"><label>Password</label><input type="password" value={password} onChange={e => setPassword(e.target.value)} required /></div>
            <button type="submit" className="primary-btn full">{isSignUp ? 'Sign Up' : 'Authenticate'}</button>
          </form>
          <p style={{textAlign: 'center', marginTop: '1rem', fontSize: '0.85rem'}}>
            <button onClick={() => { setIsSignUp(!isSignUp); setError(''); setSuccessMsg(''); }} style={{background: 'none', border: 'none', color: '#0284c7', cursor: 'pointer', fontWeight: 'bold'}}>
              {isSignUp ? 'Log In Instead' : 'Need an account? Sign Up'}
            </button>
          </p>
        </div>
      </div>
    )
  }

  return (
    <div className="dashboard-container">
      <header className="navbar">
        <div className="nav-brand">
          <span className="logo-icon">🌱</span>
          <div>
            <h1>EnviroGuard AI</h1>
            <span className="nav-subtitle">Compliance & Telemetry Suite</span>
          </div>
        </div>
        <div className="user-nav">
          <span className="status-indicator online">System Operational</span>
          <span style={{marginLeft: '10px', fontSize: '0.85rem', color: '#64748b', fontWeight: 'bold'}}>({isAdmin ? 'ADMIN' : 'USER'})</span>
          <button onClick={handleLogout} className="logout-btn" style={{marginLeft: '15px'}}>Log Out</button>
        </div>
      </header>

      <main className="dashboard-content">
        <section className="grid-layout">
          {isAdmin && (
            <div className="card control-panel">
              <div className="card-header">
                <h3>Audit Control Panel</h3>
                <span className="badge info" style={{background: '#fee2e2', color: '#991b1b'}}>Admin Only</span>
              </div>
              <form onSubmit={runAudit}>
                <div className="input-group">
                  <label>Target Sensor Unit</label>
                  <select value={sensorId} onChange={e => setSensorId(e.target.value)} style={{ padding: '0.65rem', borderRadius: '6px', border: '1px solid #cbd5e1', width: '100%', fontSize: '0.95rem' }}>
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
          )}

          <div className={`card stats-panel ${!isAdmin ? 'full-width-stats' : ''}`}>
            <div className="card-header">
              <h3>Sensor Quick Status</h3>
            </div>
            <div className="metrics-grid">
              <div className="metric-box">
                <span className="metric-label">SO2 Reading</span>
                <span className="metric-value warning">{telemetryData.length > 0 ? telemetryData[telemetryData.length-1].so2Level : 'N/A'}</span>
                <span className="metric-sub">Latest Read</span>
              </div>
              <div className="metric-box">
                <span className="metric-label">NOx Level</span>
                <span className="metric-value safe">{telemetryData.length > 0 ? telemetryData[telemetryData.length-1].noxLevel : '--'} <small>ppm</small></span>
                <span className="metric-sub">Limit: 15.0 ppm</span>
              </div>
              <div className="metric-box">
                <span className="metric-label">CO Level</span>
                <span className="metric-value safe">{telemetryData.length > 0 ? telemetryData[telemetryData.length-1].coLevel : '--'} <small>ppm</small></span>
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
            <button onClick={downloadCSV} className="primary-btn" style={{ padding: '0.4rem 0.8rem', fontSize: '0.85rem' }}>
              ⬇ Download CSV
            </button>
          </div>
          <div style={{ width: '100%', height: '320px', marginTop: '1rem' }}>
            <ResponsiveContainer>
              <LineChart data={telemetryData} margin={{ top: 5, right: 20, bottom: 5, left: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" vertical={false} />
                <XAxis dataKey="timestamp" stroke="#64748b" fontSize={12} tickFormatter={(tick) => tick ? new Date(tick).toLocaleTimeString() : ''} />
                <YAxis stroke="#64748b" fontSize={12} />
                <Tooltip labelFormatter={(label) => new Date(label).toLocaleString()} contentStyle={{ borderRadius: '8px' }} />
                <Legend wrapperStyle={{ paddingTop: '15px' }}/>
                <Line type="monotone" dataKey="noxLevel" stroke="#16a34a" name="NOx (ppm)" strokeWidth={2.5} dot={{ r: 4 }} activeDot={{ r: 6 }} />
                <Line type="monotone" dataKey="coLevel" stroke="#0284c7" name="CO (ppm)" strokeWidth={2.5} dot={{ r: 4 }} activeDot={{ r: 6 }} />
                <Line type="monotone" dataKey="so2Level" stroke="#d97706" name="SO2 (ppm)" strokeWidth={2.5} dot={{ r: 4 }} activeDot={{ r: 6 }} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </section>

        {aiResponse && (
          <section className="card report-card">
            <div className="card-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <h3>Official AI Audit Report ({sensorId})</h3>
                <span className="badge success">Verified Analysis</span>
              </div>
              <button onClick={downloadPDF} className="primary-btn" style={{ padding: '0.4rem 0.8rem', fontSize: '0.85rem', background: '#475569' }}>
                🖨 Export PDF Report
              </button>
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
