import { useState, useEffect } from 'react';
import { api } from '../api';
import { Activity, Clock, TrendingUp, AlertTriangle, Building2 } from 'lucide-react';

export default function Dashboard() {
  const [stats, setStats] = useState<any>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.getStats().then(setStats).finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="text-gray-400">Loading...</div>;
  if (!stats) return <div className="text-red-400">Failed to load stats</div>;

  const cards = [
    { label: 'Decisions Today', value: stats.total_decisions_today.toLocaleString(), icon: Activity, color: 'cyan' },
    { label: 'Avg Latency', value: `${stats.avg_latency_ms}ms`, icon: Clock, color: 'green' },
    { label: 'P99 Latency', value: `${stats.p99_latency_ms}ms`, icon: Clock, color: stats.p99_latency_ms > 180 ? 'yellow' : 'green' },
    { label: 'Offer Attach Rate', value: `${(stats.offer_attach_rate * 100).toFixed(1)}%`, icon: TrendingUp, color: 'blue' },
    { label: 'Degraded Rate', value: `${(stats.degraded_rate * 100).toFixed(1)}%`, icon: AlertTriangle, color: stats.degraded_rate > 0.05 ? 'red' : 'green' },
    { label: 'Active Tenants', value: stats.active_tenants, icon: Building2, color: 'purple' },
  ];

  return (
    <div>
      <h1 className="text-2xl font-bold text-white mb-8">Platform Dashboard</h1>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 mb-8">
        {cards.map(({ label, value, icon: Icon, color }) => (
          <div key={label} className="bg-gray-900 border border-gray-800 rounded-xl p-6">
            <div className="flex items-center justify-between mb-4">
              <span className="text-sm text-gray-400">{label}</span>
              <Icon className={`w-5 h-5 text-${color}-400`} />
            </div>
            <p className="text-3xl font-bold text-white">{value}</p>
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-8">
        {/* Decisions by Channel */}
        <div className="bg-gray-900 border border-gray-800 rounded-xl p-6">
          <h2 className="text-lg font-semibold text-white mb-4">Decisions by Channel</h2>
          <div className="space-y-3">
            {Object.entries(stats.decisions_by_channel).map(([channel, count]: [string, any]) => {
              const total = Object.values(stats.decisions_by_channel as Record<string, number>).reduce((a: number, b: number) => a + b, 0);
              const pct = ((count / total) * 100).toFixed(1);
              return (
                <div key={channel}>
                  <div className="flex justify-between text-sm mb-1">
                    <span className="text-gray-300 capitalize">{channel}</span>
                    <span className="text-gray-400">{count.toLocaleString()} ({pct}%)</span>
                  </div>
                  <div className="h-2 bg-gray-800 rounded-full overflow-hidden">
                    <div className="h-full bg-cyan-500 rounded-full" style={{ width: `${pct}%` }} />
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Decisions by Churn Band */}
        <div className="bg-gray-900 border border-gray-800 rounded-xl p-6">
          <h2 className="text-lg font-semibold text-white mb-4">Churn Distribution</h2>
          <div className="space-y-3">
            {Object.entries(stats.decisions_by_churn_band).map(([band, count]: [string, any]) => {
              const colors: Record<string, string> = { LOW: 'bg-green-500', MEDIUM: 'bg-yellow-500', HIGH: 'bg-orange-500', CRITICAL: 'bg-red-500' };
              const total = Object.values(stats.decisions_by_churn_band as Record<string, number>).reduce((a: number, b: number) => a + b, 0);
              const pct = ((count / total) * 100).toFixed(1);
              return (
                <div key={band}>
                  <div className="flex justify-between text-sm mb-1">
                    <span className="text-gray-300">{band}</span>
                    <span className="text-gray-400">{count.toLocaleString()} ({pct}%)</span>
                  </div>
                  <div className="h-2 bg-gray-800 rounded-full overflow-hidden">
                    <div className={`h-full ${colors[band] || 'bg-gray-500'} rounded-full`} style={{ width: `${pct}%` }} />
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>

      {/* Top Offers */}
      <div className="bg-gray-900 border border-gray-800 rounded-xl p-6">
        <h2 className="text-lg font-semibold text-white mb-4">Top Offers</h2>
        <table className="w-full">
          <thead>
            <tr className="text-left text-sm text-gray-400 border-b border-gray-800">
              <th className="pb-3">SKU</th><th className="pb-3">Name</th>
              <th className="pb-3 text-right">Offered</th><th className="pb-3 text-right">Accepted</th>
              <th className="pb-3 text-right">Attach Rate</th>
            </tr>
          </thead>
          <tbody className="text-sm">
            {stats.top_offers.map((o: any) => (
              <tr key={o.sku} className="border-b border-gray-800/50">
                <td className="py-3 font-mono text-cyan-400">{o.sku}</td>
                <td className="py-3 text-gray-300">{o.name}</td>
                <td className="py-3 text-right text-gray-300">{o.times_offered.toLocaleString()}</td>
                <td className="py-3 text-right text-gray-300">{o.times_accepted.toLocaleString()}</td>
                <td className="py-3 text-right text-green-400 font-medium">{(o.attach_rate * 100).toFixed(1)}%</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
