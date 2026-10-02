'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import Navbar from '@/components/Navbar';
import { supabase, isSupabaseConfigured } from '@/lib/supabaseClient';
import { 
  Users, 
  Activity, 
  AlertTriangle, 
  Clock, 
  Download, 
  RefreshCw,
  Zap,
  Info,
  Smartphone,
  Database
} from 'lucide-react';
import {
  ResponsiveContainer,
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
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
  const [realtimeNotification, setRealtimeNotification] = useState<string | null>(null);

  useEffect(() => {
    fetchDashboardData();

    // Setup Supabase Realtime Channel
    let channel: any = null;
    try {
      channel = supabase
        .channel('dashboard-realtime-overview')
        .on(
          'postgres_changes',
          { event: '*', schema: 'public', table: 'blink_sessions' },
          () => {
            showRealtimeToast('New blink session synced from participant!');
            fetchDashboardData();
          }
        )
        .on(
          'postgres_changes',
          { event: 'INSERT', schema: 'public', table: 'blink_minute_log' },
          (payload) => {
            const newLog = payload.new as any;
            if (newLog) {
              setRecentMinuteLogs((prev) => [
                ...prev.slice(-99),
                {
                  time: format(new Date(newLog.minute_timestamp), 'MM/dd HH:mm'),
                  bpm: Number(newLog.bpm),
                }
              ]);
              showRealtimeToast(`Live BPM received: ${Number(newLog.bpm).toFixed(1)} BPM`);
            }
          }
        )
        .subscribe();
    } catch (ignored) {}

    return () => {
      if (channel) {
        try {
          supabase.removeChannel(channel);
        } catch (ignored) {}
      }
    };
  }, [dateRangeDays]);

  const showRealtimeToast = (msg: string) => {
    setRealtimeNotification(msg);
    setTimeout(() => {
      setRealtimeNotification(null);
    }, 4000);
  };

  async function fetchDashboardData() {
    setLoading(true);
    try {
      const { data: { session } } = await supabase.auth.getSession();
      if (!session) {
        router.push('/login/');
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
        {/* Realtime Live Toast */}
        {realtimeNotification && (
          <div className="fixed bottom-6 right-6 z-50 bg-slate-900 text-white px-4 py-3 rounded-2xl shadow-xl border border-slate-800 flex items-center space-x-3 text-sm animate-bounce">
            <Zap className="w-4 h-4 text-emerald-400" />
            <span>{realtimeNotification}</span>
          </div>
        )}

        {/* Top Controls Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-8">
          <div>
            <div className="flex items-center space-x-2">
              <h1 className="text-2xl font-bold text-slate-900">Realtime Analytics Overview</h1>
              <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-bold bg-emerald-100 text-emerald-800">
                Live
              </span>
            </div>
            <p className="text-sm text-slate-500">Live aggregate metrics across all opt-in research participants</p>
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
              disabled={recentMinuteLogs.length === 0}
              className="inline-flex items-center px-4 py-2 bg-teal-600 hover:bg-teal-700 text-white text-sm font-semibold rounded-xl shadow-sm transition-colors disabled:opacity-50"
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

        {/* Empty State / Quickstart Banner if no sessions yet */}
        {!loading && totalUsers === 0 && totalSessions === 0 && (
          <div className="mb-8 p-6 bg-white rounded-2xl border border-slate-200 shadow-sm">
            <div className="flex items-start space-x-4">
              <div className="p-3 bg-teal-50 rounded-2xl text-teal-600 flex-shrink-0">
                <Info className="w-6 h-6" />
              </div>
              <div className="flex-1 space-y-3">
                <h3 className="text-base font-bold text-slate-900">Waiting for Participant Data</h3>
                <p className="text-xs text-slate-600 leading-relaxed">
                  Your Supabase portal is active! As soon as an Android participant opens BlinkWell with <strong>Research Data Sharing</strong> enabled and runs a monitoring session, their pseudonymous metrics will automatically appear and stream here in real time.
                </p>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs pt-2">
                  <div className="p-3 rounded-xl bg-slate-50 border border-slate-200 flex items-start space-x-2.5">
                    <Database className="w-4 h-4 text-teal-600 flex-shrink-0 mt-0.5" />
                    <div>
                      <strong className="block text-slate-800">1. Database Schema</strong>
                      <span className="text-slate-500">Ensure <code className="bg-white px-1.5 py-0.5 rounded border text-[11px]">supabase/schema.sql</code> is executed in your Supabase SQL Editor.</span>
                    </div>
                  </div>
                  <div className="p-3 rounded-xl bg-slate-50 border border-slate-200 flex items-start space-x-2.5">
                    <Smartphone className="w-4 h-4 text-teal-600 flex-shrink-0 mt-0.5" />
                    <div>
                      <strong className="block text-slate-800">2. Android App Sync</strong>
                      <span className="text-slate-500">Enable &quot;Contribute to Research&quot; in the mobile app to sync anonymized session logs.</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Charts Section */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-8">
          {/* Main BPM Trend Chart */}
          <div className="lg:col-span-2 bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
            <div className="flex justify-between items-center mb-4">
              <h2 className="text-base font-bold text-slate-900">Live Blink Rate Trend (BPM)</h2>
              <span className="text-xs text-slate-400 flex items-center">
                <span className="w-2 h-2 rounded-full bg-emerald-500 mr-1.5 animate-ping"></span>
                Streaming Updates
              </span>
            </div>
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
