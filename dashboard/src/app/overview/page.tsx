'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import Navbar from '@/components/Navbar';
import { supabase } from '@/lib/supabaseClient';
import { 
  Users, 
  Activity, 
  AlertTriangle, 
  Clock, 
  Download,
  Filter,
  RefreshCw
} from 'lucide-react';
import {
  ResponsiveContainer,
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  BarChart,
  Bar,
  PieChart,
  Pie,
  Cell
} from 'recharts';
import { format, subDays } from 'date-fns';

export default function OverviewPage() {
  const router = useRouter();
  const [loading, setLoading] = useState(true);
  const [totalUsers, setTotalUsers] = useState(0);
  const [totalSessions, setTotalSessions] = useState(0);
  const [avgBpm, setAvgBpm] = useState(0);
  const [totalAlerts, setTotalAlerts] = useState(0);
  const [recentMinuteLogs, setRecentMinuteLogs] = useState<any[]>([]);
  const [modeBreakdown, setModeBreakdown] = useState<any[]>([]);
  const [dateRangeDays, setDateRangeDays] = useState(7);

  useEffect(() => {
    fetchDashboardData();
  }, [dateRangeDays]);

  async function fetchDashboardData() {
    setLoading(true);
    try {
      const { data: { session } } = await supabase.auth.getSession();
      if (!session) {
        router.push('/login');
        return;
      }

      const sinceDate = subDays(new Date(), dateRangeDays).toISOString();

      // 1. Fetch consented users count
      const { count: usersCount } = await supabase
        .from('profiles')
        .select('*', { count: 'exact', head: true })
        .eq('research_consent', true);

      setTotalUsers(usersCount || 0);

      // 2. Fetch Sessions
      const { data: sessions } = await supabase
        .from('blink_sessions')
        .select('*')
        .gte('started_at', sinceDate);

      if (sessions && sessions.length > 0) {
        setTotalSessions(sessions.length);
        const validBpmSessions = sessions.filter(s => s.avg_bpm != null && s.avg_bpm > 0);
        const overallAvg = validBpmSessions.length > 0
          ? validBpmSessions.reduce((acc, s) => acc + Number(s.avg_bpm), 0) / validBpmSessions.length
          : 0;
        setAvgBpm(overallAvg);

        const alerts = sessions.reduce((acc, s) => acc + (s.alert_count || 0), 0);
        setTotalAlerts(alerts);

        // Mode breakdown
        const bgCount = sessions.filter(s => s.monitoring_mode === 'background').length;
        const appCount = sessions.filter(s => s.monitoring_mode === 'app_only').length;
        setModeBreakdown([
          { name: 'Background Mode', value: bgCount, color: '#0d9488' },
          { name: 'App-Only Mode', value: appCount, color: '#5eead4' },
        ]);
      } else {
        setTotalSessions(0);
        setAvgBpm(0);
        setTotalAlerts(0);
        setModeBreakdown([]);
      }

      // 3. Fetch Recent Minute Logs for timeline chart
      const { data: logs } = await supabase
        .from('blink_minute_log')
        .select('*')
        .gte('minute_timestamp', sinceDate)
        .order('minute_timestamp', { ascending: true })
        .limit(100);

      if (logs) {
        const formattedLogs = logs.map(l => ({
          time: format(new Date(l.minute_timestamp), 'MM/dd HH:mm'),
          bpm: Number(l.bpm),
        }));
        setRecentMinuteLogs(formattedLogs);
      }
    } catch (err) {
      console.error('Error fetching dashboard data:', err);
    } finally {
      setLoading(false);
    }
  }

  const exportCSV = () => {
    if (!recentMinuteLogs.length) return;
    const headers = 'Timestamp,BPM\n';
    const rows = recentMinuteLogs.map(r => `"${r.time}",${r.bpm}`).join('\n');
    const blob = new Blob([headers + rows], { type: 'text/csv' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `blinkwell-metrics-${format(new Date(), 'yyyy-MM-dd')}.csv`;
    a.click();
  };

  return (
    <div className="min-h-screen bg-slate-50">
      <Navbar />

      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* Top Controls Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-8">
          <div>
            <h1 className="text-2xl font-bold text-slate-900">Research Analytics Overview</h1>
            <p className="text-sm text-slate-500">Aggregate metrics across all opt-in participants</p>
          </div>

          <div className="flex items-center space-x-3">
            <select
              value={dateRangeDays}
              onChange={(e) => setDateRangeDays(Number(e.target.value))}
              className="bg-white border border-slate-300 text-slate-700 text-sm rounded-xl px-3 py-2 focus:ring-2 focus:ring-teal-500 focus:outline-none"
            >
              <option value={1}>Last 24 Hours</option>
              <option value={7}>Last 7 Days</option>
              <option value={30}>Last 30 Days</option>
              <option value={90}>Last 90 Days</option>
            </select>

            <button
              onClick={fetchDashboardData}
              className="p-2 bg-white border border-slate-300 rounded-xl text-slate-600 hover:text-slate-900 hover:bg-slate-100 transition-colors"
              title="Refresh Data"
            >
              <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
            </button>

            <button
              onClick={exportCSV}
              className="inline-flex items-center px-4 py-2 bg-teal-600 hover:bg-teal-700 text-white text-sm font-semibold rounded-xl shadow-sm transition-colors"
            >
              <Download className="w-4 h-4 mr-2" />
              Export CSV
            </button>
          </div>
        </div>

        {/* Metric Summary Cards */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
          <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
            <div className="flex items-center justify-between">
              <span className="text-sm font-medium text-slate-500">Consented Users</span>
              <div className="p-2 bg-teal-50 rounded-xl">
                <Users className="w-5 h-5 text-teal-600" />
              </div>
            </div>
            <div className="mt-4">
              <span className="text-3xl font-bold text-slate-900">{totalUsers}</span>
              <span className="text-xs text-slate-400 block mt-1">Opted into anonymous research</span>
            </div>
          </div>

          <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
            <div className="flex items-center justify-between">
              <span className="text-sm font-medium text-slate-500">Average Blink Rate</span>
              <div className="p-2 bg-emerald-50 rounded-xl">
                <Activity className="w-5 h-5 text-emerald-600" />
              </div>
            </div>
            <div className="mt-4">
              <span className="text-3xl font-bold text-slate-900">{avgBpm.toFixed(1)}</span>
              <span className="text-sm font-medium text-slate-500 ml-1">BPM</span>
              <span className="text-xs text-slate-400 block mt-1">Target range: 15–20 BPM</span>
            </div>
          </div>

          <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
            <div className="flex items-center justify-between">
              <span className="text-sm font-medium text-slate-500">Low-Blink Alerts</span>
              <div className="p-2 bg-rose-50 rounded-xl">
                <AlertTriangle className="w-5 h-5 text-rose-600" />
              </div>
            </div>
            <div className="mt-4">
              <span className="text-3xl font-bold text-slate-900">{totalAlerts}</span>
              <span className="text-xs text-slate-400 block mt-1">Sustained rate &lt; threshold</span>
            </div>
          </div>

          <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
            <div className="flex items-center justify-between">
              <span className="text-sm font-medium text-slate-500">Recorded Sessions</span>
              <div className="p-2 bg-blue-50 rounded-xl">
                <Clock className="w-5 h-5 text-blue-600" />
              </div>
            </div>
            <div className="mt-4">
              <span className="text-3xl font-bold text-slate-900">{totalSessions}</span>
              <span className="text-xs text-slate-400 block mt-1">Within selected period</span>
            </div>
          </div>
        </div>

        {/* Charts Section */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-8">
          {/* Main BPM Trend Chart */}
          <div className="lg:col-span-2 bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
            <h2 className="text-base font-bold text-slate-900 mb-4">Aggregate Blink Rate Trend (BPM)</h2>
            <div className="h-72 w-full">
              {recentMinuteLogs.length > 0 ? (
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={recentMinuteLogs}>
                    <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e2e8f0" />
                    <XAxis dataKey="time" stroke="#94a3b8" fontSize={12} tickLine={false} />
                    <YAxis domain={[0, 30]} stroke="#94a3b8" fontSize={12} tickLine={false} />
                    <Tooltip 
                      contentStyle={{ backgroundColor: '#ffffff', borderRadius: '12px', borderColor: '#e2e8f0' }}
                    />
                    <Line 
                      type="monotone" 
                      dataKey="bpm" 
                      stroke="#0d9488" 
                      strokeWidth={2}
                      dot={false}
                      activeDot={{ r: 5 }}
                    />
                  </LineChart>
                </ResponsiveContainer>
              ) : (
                <div className="flex items-center justify-center h-full text-sm text-slate-400">
                  No minute logs recorded in this date range.
                </div>
              )}
            </div>
          </div>

          {/* Mode Distribution Chart */}
          <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm flex flex-col justify-between">
            <h2 className="text-base font-bold text-slate-900 mb-4">Monitoring Mode Breakdown</h2>
            <div className="h-56 w-full flex items-center justify-center">
              {modeBreakdown.length > 0 ? (
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={modeBreakdown}
                      cx="50%"
                      cy="50%"
                      innerRadius={50}
                      outerRadius={80}
                      paddingAngle={4}
                      dataKey="value"
                    >
                      {modeBreakdown.map((entry, index) => (
                        <Cell key={`cell-${index}`} fill={entry.color} />
                      ))}
                    </Pie>
                    <Tooltip />
                  </PieChart>
                </ResponsiveContainer>
              ) : (
                <div className="text-sm text-slate-400">No session mode data available.</div>
              )}
            </div>

            <div className="flex justify-around border-t border-slate-100 pt-4">
              {modeBreakdown.map((mode, i) => (
                <div key={i} className="text-center">
                  <span className="text-xs text-slate-500 block">{mode.name}</span>
                  <span className="text-lg font-bold text-slate-800">{mode.value}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}
