import { useState, useEffect } from 'react';
import { api } from '../api';
import { Plus, Trash2, UserCheck, UserX } from 'lucide-react';

export default function Users() {
  const [users, setUsers] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm] = useState({ email: '', name: '', password: '', role: 'ANALYST', tenant_id: '' });

  const load = () => api.listUsers().then(setUsers).finally(() => setLoading(false));
  useEffect(() => { load(); }, []);

  const create = async () => {
    await api.createUser({ ...form, tenant_id: form.tenant_id || null });
    setShowCreate(false);
    setForm({ email: '', name: '', password: '', role: 'ANALYST', tenant_id: '' });
    load();
  };

  const toggleActive = async (user: any) => {
    await api.updateUser(user.id, { active: !user.active });
    load();
  };

  const deleteUser = async (id: string) => {
    if (!confirm('Delete this user?')) return;
    await api.deleteUser(id);
    load();
  };

  const roleColors: Record<string, string> = {
    SUPER_ADMIN: 'text-red-400 bg-red-500/10 border-red-500/20',
    TENANT_ADMIN: 'text-orange-400 bg-orange-500/10 border-orange-500/20',
    ANALYST: 'text-blue-400 bg-blue-500/10 border-blue-500/20',
    VIEWER: 'text-gray-400 bg-gray-500/10 border-gray-500/20',
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold text-white">User Management</h1>
          <p className="text-gray-400 mt-1">Manage platform and tenant users</p>
        </div>
        <button onClick={() => setShowCreate(true)} className="flex items-center gap-2 px-4 py-2 bg-cyan-600 hover:bg-cyan-500 text-white rounded-lg transition-colors">
          <Plus className="w-4 h-4" /> Add User
        </button>
      </div>

      {showCreate && (
        <div className="bg-gray-900 border border-cyan-500/30 rounded-xl p-6 mb-8">
          <h2 className="text-lg font-semibold text-white mb-4">Create User</h2>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <input value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} placeholder="Full Name" className="px-4 py-2 bg-gray-800 border border-gray-700 rounded-lg text-white outline-none focus:ring-2 focus:ring-cyan-500" />
            <input type="email" value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} placeholder="Email" className="px-4 py-2 bg-gray-800 border border-gray-700 rounded-lg text-white outline-none focus:ring-2 focus:ring-cyan-500" />
            <input type="password" value={form.password} onChange={e => setForm({ ...form, password: e.target.value })} placeholder="Password" className="px-4 py-2 bg-gray-800 border border-gray-700 rounded-lg text-white outline-none focus:ring-2 focus:ring-cyan-500" />
            <select value={form.role} onChange={e => setForm({ ...form, role: e.target.value })} className="px-4 py-2 bg-gray-800 border border-gray-700 rounded-lg text-white outline-none focus:ring-2 focus:ring-cyan-500">
              <option value="SUPER_ADMIN">Super Admin</option><option value="TENANT_ADMIN">Tenant Admin</option><option value="ANALYST">Analyst</option><option value="VIEWER">Viewer</option>
            </select>
          </div>
          <div className="flex gap-3 mt-4">
            <button onClick={create} className="px-6 py-2 bg-cyan-600 hover:bg-cyan-500 text-white rounded-lg transition-colors">Create</button>
            <button onClick={() => setShowCreate(false)} className="px-6 py-2 bg-gray-800 hover:bg-gray-700 text-gray-300 rounded-lg transition-colors">Cancel</button>
          </div>
        </div>
      )}

      {loading ? <p className="text-gray-400">Loading...</p> : (
        <div className="bg-gray-900 border border-gray-800 rounded-xl overflow-hidden">
          <table className="w-full">
            <thead>
              <tr className="text-left text-sm text-gray-400 border-b border-gray-800">
                <th className="px-6 py-4">User</th><th className="px-6 py-4">Role</th><th className="px-6 py-4">Tenant</th><th className="px-6 py-4">Status</th><th className="px-6 py-4">Last Login</th><th className="px-6 py-4"></th>
              </tr>
            </thead>
            <tbody className="text-sm">
              {users.map((u: any) => (
                <tr key={u.id} className="border-b border-gray-800/50 hover:bg-gray-800/30">
                  <td className="px-6 py-4">
                    <p className="text-white font-medium">{u.name}</p>
                    <p className="text-gray-500 text-xs">{u.email}</p>
                  </td>
                  <td className="px-6 py-4">
                    <span className={`px-2 py-1 text-xs rounded-md border ${roleColors[u.role] || ''}`}>{u.role}</span>
                  </td>
                  <td className="px-6 py-4 text-gray-400">{u.tenant_name || 'Platform'}</td>
                  <td className="px-6 py-4">
                    <button onClick={() => toggleActive(u)} className={`flex items-center gap-1.5 text-xs ${u.active ? 'text-green-400' : 'text-red-400'}`}>
                      {u.active ? <UserCheck className="w-3.5 h-3.5" /> : <UserX className="w-3.5 h-3.5" />}
                      {u.active ? 'Active' : 'Disabled'}
                    </button>
                  </td>
                  <td className="px-6 py-4 text-gray-500 text-xs">{u.last_login_at || 'Never'}</td>
                  <td className="px-6 py-4">
                    <button onClick={() => deleteUser(u.id)} className="text-gray-500 hover:text-red-400 transition-colors">
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
