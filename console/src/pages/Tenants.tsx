import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api';
import { Plus, Building2, CheckCircle, XCircle } from 'lucide-react';

export default function Tenants() {
  const [tenants, setTenants] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [showCreate, setShowCreate] = useState(false);
  const navigate = useNavigate();

  const load = () => api.listTenants().then(setTenants).finally(() => setLoading(false));
  useEffect(() => { load(); }, []);

  const [form, setForm] = useState({ name: '', display_name: '', market: 'AE', regulatory_profile: { require_arabic_disclosure: false, consent_required: false, cooling_off_hours: 24, audit_retention_months: 24 } });

  const create = async () => {
    await api.createTenant(form);
    setShowCreate(false);
    setForm({ name: '', display_name: '', market: 'AE', regulatory_profile: { require_arabic_disclosure: false, consent_required: false, cooling_off_hours: 24, audit_retention_months: 24 } });
    load();
  };

  const markets: Record<string, string> = { AE: 'UAE', SA: 'Saudi Arabia', KW: 'Kuwait', BH: 'Bahrain', OM: 'Oman' };

  return (
    <div>
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold text-white">Telco Configuration</h1>
          <p className="text-gray-400 mt-1">Configure and manage telecom operator tenants</p>
        </div>
        <button onClick={() => setShowCreate(true)} className="flex items-center gap-2 px-4 py-2 bg-cyan-600 hover:bg-cyan-500 text-white rounded-lg transition-colors">
          <Plus className="w-4 h-4" /> Add Telco
        </button>
      </div>

      {showCreate && (
        <div className="bg-gray-900 border border-cyan-500/30 rounded-xl p-6 mb-8">
          <h2 className="text-lg font-semibold text-white mb-4">Onboard New Telco</h2>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm text-gray-400 mb-1">Slug (lowercase, no spaces)</label>
              <input value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} className="w-full px-4 py-2 bg-gray-800 border border-gray-700 rounded-lg text-white outline-none focus:ring-2 focus:ring-cyan-500" placeholder="etisalat-uae" />
            </div>
            <div>
              <label className="block text-sm text-gray-400 mb-1">Display Name</label>
              <input value={form.display_name} onChange={e => setForm({ ...form, display_name: e.target.value })} className="w-full px-4 py-2 bg-gray-800 border border-gray-700 rounded-lg text-white outline-none focus:ring-2 focus:ring-cyan-500" placeholder="Etisalat UAE" />
            </div>
            <div>
              <label className="block text-sm text-gray-400 mb-1">Market</label>
              <select value={form.market} onChange={e => setForm({ ...form, market: e.target.value })} className="w-full px-4 py-2 bg-gray-800 border border-gray-700 rounded-lg text-white outline-none focus:ring-2 focus:ring-cyan-500">
                {Object.entries(markets).map(([code, name]) => <option key={code} value={code}>{name} ({code})</option>)}
              </select>
            </div>
            <div className="flex items-end gap-2">
              <label className="flex items-center gap-2 text-sm text-gray-300">
                <input type="checkbox" checked={form.regulatory_profile.require_arabic_disclosure} onChange={e => setForm({ ...form, regulatory_profile: { ...form.regulatory_profile, require_arabic_disclosure: e.target.checked } })} className="rounded bg-gray-800 border-gray-700 text-cyan-500" />
                Require Arabic disclosure
              </label>
            </div>
          </div>
          <div className="flex gap-3 mt-6">
            <button onClick={create} className="px-6 py-2 bg-cyan-600 hover:bg-cyan-500 text-white rounded-lg transition-colors">Create Telco</button>
            <button onClick={() => setShowCreate(false)} className="px-6 py-2 bg-gray-800 hover:bg-gray-700 text-gray-300 rounded-lg transition-colors">Cancel</button>
          </div>
        </div>
      )}

      {loading ? <p className="text-gray-400">Loading...</p> : (
        <div className="grid gap-4">
          {tenants.map((t: any) => (
            <div key={t.id} onClick={() => navigate(`/tenants/${t.id}`)} className="bg-gray-900 border border-gray-800 hover:border-cyan-500/30 rounded-xl p-6 cursor-pointer transition-colors">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-4">
                  <Building2 className="w-10 h-10 text-cyan-400" />
                  <div>
                    <h3 className="text-lg font-semibold text-white">{t.display_name}</h3>
                    <p className="text-sm text-gray-400">{t.name} &middot; {markets[t.market] || t.market}</p>
                  </div>
                </div>
                <div className="flex items-center gap-4">
                  <span className={`flex items-center gap-1.5 text-sm ${t.status === 'ACTIVE' ? 'text-green-400' : 'text-red-400'}`}>
                    {t.status === 'ACTIVE' ? <CheckCircle className="w-4 h-4" /> : <XCircle className="w-4 h-4" />}
                    {t.status}
                  </span>
                  <span className="text-sm text-gray-500">API: {t.api_credentials?.client_id?.substring(0, 8)}...</span>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
