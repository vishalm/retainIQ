import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { api } from '../api';
import { ArrowLeft, Save, TestTube, RefreshCw, CheckCircle, XCircle, Plug, Shield, Sliders, Zap, Key } from 'lucide-react';

export default function TenantConfig() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [tenant, setTenant] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [bssTest, setBssTest] = useState<any>(null);
  const [newCreds, setNewCreds] = useState<any>(null);
  const [tab, setTab] = useState('general');

  // Form state
  const [form, setForm] = useState<any>({});

  useEffect(() => {
    if (!id) return;
    api.getTenant(id).then(t => { setTenant(t); setForm(t); }).finally(() => setLoading(false));
  }, [id]);

  const save = async () => {
    if (!id) return;
    setSaving(true);
    try {
      const updated = await api.updateTenant(id, {
        display_name: form.display_name,
        regulatory_profile: form.regulatory_profile,
        bss_config: form.bss_config,
        catalog_webhook_url: form.catalog_webhook_url,
        ranking_weights: form.ranking_weights,
        rate_limits: form.rate_limits,
      });
      setTenant(updated);
      setForm(updated);
    } finally { setSaving(false); }
  };

  const testBssConn = async () => {
    if (!id) return;
    setBssTest(null);
    const result = await api.testBss(id);
    setBssTest(result);
  };

  const regenCreds = async () => {
    if (!id || !confirm('Regenerate API credentials? The old secret will stop working immediately.')) return;
    const creds = await api.regenerateCredentials(id);
    setNewCreds(creds);
  };

  if (loading) return <div className="text-gray-400">Loading...</div>;
  if (!tenant) return <div className="text-red-400">Tenant not found</div>;

  const tabs = [
    { id: 'general', label: 'General', icon: Sliders },
    { id: 'bss', label: 'BSS Connection', icon: Plug },
    { id: 'compliance', label: 'Compliance', icon: Shield },
    { id: 'ranking', label: 'Offer Ranking', icon: Zap },
    { id: 'credentials', label: 'API Credentials', icon: Key },
  ];

  return (
    <div>
      <button onClick={() => navigate('/tenants')} className="flex items-center gap-2 text-gray-400 hover:text-white mb-6 transition-colors">
        <ArrowLeft className="w-4 h-4" /> Back to Telco List
      </button>

      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold text-white">{tenant.display_name}</h1>
          <p className="text-gray-400 mt-1">{tenant.name} &middot; Market: {tenant.market} &middot; Status: <span className={tenant.status === 'ACTIVE' ? 'text-green-400' : 'text-red-400'}>{tenant.status}</span></p>
        </div>
        <div className="flex gap-3">
          {tenant.status === 'ACTIVE' ? (
            <button onClick={() => api.suspendTenant(id!).then(setTenant)} className="px-4 py-2 bg-red-600/20 text-red-400 border border-red-500/30 rounded-lg hover:bg-red-600/30 transition-colors">Suspend</button>
          ) : (
            <button onClick={() => api.activateTenant(id!).then(setTenant)} className="px-4 py-2 bg-green-600/20 text-green-400 border border-green-500/30 rounded-lg hover:bg-green-600/30 transition-colors">Activate</button>
          )}
          <button onClick={save} disabled={saving} className="flex items-center gap-2 px-6 py-2 bg-cyan-600 hover:bg-cyan-500 text-white rounded-lg transition-colors disabled:opacity-50">
            <Save className="w-4 h-4" /> {saving ? 'Saving...' : 'Save Changes'}
          </button>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 mb-6 border-b border-gray-800">
        {tabs.map(({ id: tid, label, icon: Icon }) => (
          <button key={tid} onClick={() => setTab(tid)} className={`flex items-center gap-2 px-4 py-3 text-sm font-medium border-b-2 transition-colors ${tab === tid ? 'border-cyan-400 text-cyan-400' : 'border-transparent text-gray-400 hover:text-gray-200'}`}>
            <Icon className="w-4 h-4" /> {label}
          </button>
        ))}
      </div>

      {/* General Tab */}
      {tab === 'general' && (
        <div className="bg-gray-900 border border-gray-800 rounded-xl p-6 space-y-6">
          <h2 className="text-lg font-semibold text-white">General Settings</h2>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <Field label="Display Name" value={form.display_name} onChange={v => setForm({ ...form, display_name: v })} />
            <Field label="Catalog Webhook URL" value={form.catalog_webhook_url || ''} onChange={v => setForm({ ...form, catalog_webhook_url: v })} placeholder="https://your-vas-platform.com/webhook" />
            <div>
              <label className="block text-sm text-gray-400 mb-1">Rate Limit (req/s)</label>
              <input type="number" value={form.rate_limits?.requests_per_second || 1000} onChange={e => setForm({ ...form, rate_limits: { ...form.rate_limits, requests_per_second: parseInt(e.target.value) } })} className="w-full px-4 py-2 bg-gray-800 border border-gray-700 rounded-lg text-white outline-none focus:ring-2 focus:ring-cyan-500" />
            </div>
            <div>
              <label className="block text-sm text-gray-400 mb-1">Burst Size</label>
              <input type="number" value={form.rate_limits?.burst_size || 5000} onChange={e => setForm({ ...form, rate_limits: { ...form.rate_limits, burst_size: parseInt(e.target.value) } })} className="w-full px-4 py-2 bg-gray-800 border border-gray-700 rounded-lg text-white outline-none focus:ring-2 focus:ring-cyan-500" />
            </div>
          </div>
        </div>
      )}

      {/* BSS Connection Tab */}
      {tab === 'bss' && (
        <div className="bg-gray-900 border border-gray-800 rounded-xl p-6 space-y-6">
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-semibold text-white">BSS / Billing Connection</h2>
            <button onClick={testBssConn} className="flex items-center gap-2 px-4 py-2 bg-gray-800 hover:bg-gray-700 text-gray-300 rounded-lg transition-colors">
              <TestTube className="w-4 h-4" /> Test Connection
            </button>
          </div>
          {bssTest && (
            <div className={`flex items-center gap-3 p-4 rounded-lg border ${bssTest.success ? 'bg-green-500/10 border-green-500/30 text-green-400' : 'bg-red-500/10 border-red-500/30 text-red-400'}`}>
              {bssTest.success ? <CheckCircle className="w-5 h-5" /> : <XCircle className="w-5 h-5" />}
              {bssTest.message} ({bssTest.response_time_ms}ms)
            </div>
          )}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <label className="block text-sm text-gray-400 mb-1">Protocol</label>
              <select value={form.bss_config?.type || 'rest'} onChange={e => setForm({ ...form, bss_config: { ...form.bss_config, type: e.target.value } })} className="w-full px-4 py-2 bg-gray-800 border border-gray-700 rounded-lg text-white outline-none focus:ring-2 focus:ring-cyan-500">
                <option value="rest">REST API</option><option value="soap">SOAP / XML</option>
              </select>
            </div>
            <Field label="BSS Endpoint" value={form.bss_config?.endpoint || ''} onChange={v => setForm({ ...form, bss_config: { ...form.bss_config, endpoint: v } })} placeholder="https://bss.operator.com/api" />
            <div>
              <label className="block text-sm text-gray-400 mb-1">Auth Type</label>
              <select value={form.bss_config?.auth_type || 'oauth2'} onChange={e => setForm({ ...form, bss_config: { ...form.bss_config, auth_type: e.target.value } })} className="w-full px-4 py-2 bg-gray-800 border border-gray-700 rounded-lg text-white outline-none focus:ring-2 focus:ring-cyan-500">
                <option value="oauth2">OAuth 2.0</option><option value="basic">Basic Auth</option><option value="api_key">API Key</option><option value="mtls">mTLS</option>
              </select>
            </div>
            <Field label="Timeout (ms)" value={String(form.bss_config?.timeout_ms || 5000)} onChange={v => setForm({ ...form, bss_config: { ...form.bss_config, timeout_ms: parseInt(v) } })} />
          </div>
          <div>
            <h3 className="text-sm font-medium text-gray-300 mb-3">Field Mapping (RetainIQ field → BSS field)</h3>
            <div className="bg-gray-800 rounded-lg p-4 font-mono text-sm text-gray-300 space-y-1">
              <p>subscriber_id → customer_number</p>
              <p>arpu → monthly_revenue</p>
              <p>tenure_days → account_age_days</p>
              <p>segment → customer_segment</p>
              <p className="text-gray-500 italic">Edit via YAML config or API</p>
            </div>
          </div>
        </div>
      )}

      {/* Compliance Tab */}
      {tab === 'compliance' && (
        <div className="bg-gray-900 border border-gray-800 rounded-xl p-6 space-y-6">
          <h2 className="text-lg font-semibold text-white">Regulatory Compliance</h2>
          <div className="space-y-4">
            <Toggle label="Require Arabic disclosure for all offers" desc="Mandatory for KSA (NCA/CITC). Offers without Arabic text will be excluded from ranking." checked={form.regulatory_profile?.require_arabic_disclosure || false} onChange={v => setForm({ ...form, regulatory_profile: { ...form.regulatory_profile, require_arabic_disclosure: v } })} />
            <Toggle label="Require explicit consent before VAS activation" desc="CITC mandate for Saudi Arabia. UAE allows opt-out with cooling-off period." checked={form.regulatory_profile?.consent_required || false} onChange={v => setForm({ ...form, regulatory_profile: { ...form.regulatory_profile, consent_required: v } })} />
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div>
                <label className="block text-sm text-gray-400 mb-1">Cooling-off Period (hours)</label>
                <input type="number" value={form.regulatory_profile?.cooling_off_hours || 24} onChange={e => setForm({ ...form, regulatory_profile: { ...form.regulatory_profile, cooling_off_hours: parseInt(e.target.value) } })} className="w-full px-4 py-2 bg-gray-800 border border-gray-700 rounded-lg text-white outline-none focus:ring-2 focus:ring-cyan-500" />
              </div>
              <div>
                <label className="block text-sm text-gray-400 mb-1">Audit Retention (months)</label>
                <input type="number" value={form.regulatory_profile?.audit_retention_months || 24} onChange={e => setForm({ ...form, regulatory_profile: { ...form.regulatory_profile, audit_retention_months: parseInt(e.target.value) } })} className="w-full px-4 py-2 bg-gray-800 border border-gray-700 rounded-lg text-white outline-none focus:ring-2 focus:ring-cyan-500" />
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Ranking Tab */}
      {tab === 'ranking' && (
        <div className="bg-gray-900 border border-gray-800 rounded-xl p-6 space-y-6">
          <h2 className="text-lg font-semibold text-white">Offer Ranking Weights</h2>
          <p className="text-sm text-gray-400">score = alpha * retention_probability + beta * margin - gamma * spend_cap_pressure + delta * context_match</p>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <Slider label="Alpha (Retention Probability)" value={form.ranking_weights?.alpha || 0.45} onChange={v => setForm({ ...form, ranking_weights: { ...form.ranking_weights, alpha: v } })} />
            <Slider label="Beta (Margin)" value={form.ranking_weights?.beta || 0.30} onChange={v => setForm({ ...form, ranking_weights: { ...form.ranking_weights, beta: v } })} />
            <Slider label="Gamma (Spend Cap Pressure)" value={form.ranking_weights?.gamma || 0.15} onChange={v => setForm({ ...form, ranking_weights: { ...form.ranking_weights, gamma: v } })} />
            <Slider label="Delta (Context Match)" value={form.ranking_weights?.delta || 0.10} onChange={v => setForm({ ...form, ranking_weights: { ...form.ranking_weights, delta: v } })} />
          </div>
          <div className="bg-gray-800 rounded-lg p-4 text-sm">
            <p className="text-gray-400">Sum: <span className="text-white font-mono">{((form.ranking_weights?.alpha || 0.45) + (form.ranking_weights?.beta || 0.30) + (form.ranking_weights?.gamma || 0.15) + (form.ranking_weights?.delta || 0.10)).toFixed(2)}</span></p>
            <p className="text-gray-500 mt-1">Weights do not need to sum to 1.0, but keeping them close helps interpretability.</p>
          </div>
        </div>
      )}

      {/* Credentials Tab */}
      {tab === 'credentials' && (
        <div className="bg-gray-900 border border-gray-800 rounded-xl p-6 space-y-6">
          <h2 className="text-lg font-semibold text-white">API Credentials</h2>
          <div className="space-y-4">
            <div>
              <label className="block text-sm text-gray-400 mb-1">Client ID (Tenant ID)</label>
              <div className="flex items-center gap-2">
                <code className="flex-1 px-4 py-2 bg-gray-800 border border-gray-700 rounded-lg text-cyan-400 font-mono text-sm">{tenant.api_credentials?.client_id}</code>
              </div>
            </div>
            <div>
              <label className="block text-sm text-gray-400 mb-1">Client Secret</label>
              {newCreds?.client_secret ? (
                <div className="p-4 bg-yellow-500/10 border border-yellow-500/30 rounded-lg">
                  <p className="text-yellow-400 text-sm mb-2">New secret (shown once -- copy now!):</p>
                  <code className="text-white font-mono text-sm break-all">{newCreds.client_secret}</code>
                </div>
              ) : (
                <p className="text-sm text-gray-500">Secret is hidden after creation. Regenerate to get a new one.</p>
              )}
            </div>
            <button onClick={regenCreds} className="flex items-center gap-2 px-4 py-2 bg-gray-800 hover:bg-gray-700 text-gray-300 rounded-lg transition-colors">
              <RefreshCw className="w-4 h-4" /> Regenerate Credentials
            </button>
          </div>
          <div className="border-t border-gray-800 pt-6">
            <h3 className="text-sm font-medium text-gray-300 mb-3">Quick Integration Test</h3>
            <pre className="bg-gray-800 rounded-lg p-4 text-sm text-gray-300 overflow-x-auto">{`curl -X POST http://localhost:8080/v1/auth/token \\
  -H "Content-Type: application/json" \\
  -d '{"grant_type":"client_credentials","client_id":"${tenant.api_credentials?.client_id}","client_secret":"YOUR_SECRET"}'`}</pre>
          </div>
        </div>
      )}
    </div>
  );
}

function Field({ label, value, onChange, placeholder }: { label: string; value: string; onChange: (v: string) => void; placeholder?: string }) {
  return (
    <div>
      <label className="block text-sm text-gray-400 mb-1">{label}</label>
      <input value={value} onChange={e => onChange(e.target.value)} placeholder={placeholder} className="w-full px-4 py-2 bg-gray-800 border border-gray-700 rounded-lg text-white outline-none focus:ring-2 focus:ring-cyan-500" />
    </div>
  );
}

function Toggle({ label, desc, checked, onChange }: { label: string; desc: string; checked: boolean; onChange: (v: boolean) => void }) {
  return (
    <div className="flex items-start gap-4 p-4 bg-gray-800 rounded-lg">
      <button onClick={() => onChange(!checked)} className={`mt-0.5 w-10 h-6 rounded-full transition-colors flex-shrink-0 ${checked ? 'bg-cyan-500' : 'bg-gray-600'}`}>
        <div className={`w-4 h-4 bg-white rounded-full m-1 transition-transform ${checked ? 'translate-x-4' : ''}`} />
      </button>
      <div>
        <p className="text-sm font-medium text-gray-200">{label}</p>
        <p className="text-xs text-gray-400 mt-1">{desc}</p>
      </div>
    </div>
  );
}

function Slider({ label, value, onChange }: { label: string; value: number; onChange: (v: number) => void }) {
  return (
    <div>
      <div className="flex justify-between mb-1">
        <label className="text-sm text-gray-400">{label}</label>
        <span className="text-sm text-cyan-400 font-mono">{value.toFixed(2)}</span>
      </div>
      <input type="range" min="0" max="1" step="0.05" value={value} onChange={e => onChange(parseFloat(e.target.value))} className="w-full accent-cyan-500" />
    </div>
  );
}
