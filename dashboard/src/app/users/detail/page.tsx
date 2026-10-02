'use client';

import { useState, useEffect, Suspense } from 'react';
import { useSearchParams } from 'next/navigation';
import Link from 'next/link';
import Navbar from '@/components/Navbar';
import { supabase } from '@/lib/supabaseClient';
import { ArrowLeft, ShieldCheck, Zap } from 'lucide-react';
import {
  ResponsiveContainer,
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip
} from 'recharts';
import { format } from 'date-fns';

function UserDetailContent() {
  const searchParams = useSearchParams();
  const userId = searchParams.get('id');

  const [loading, setLoading] = useState(true);
  const [sessions, setSessions] = useState<any[]>([]);
  const [selectedSession, setSelectedSession] = useState<any | null>(null);
  const [sessionLogs, setSessionLogs] = useState<any[]>([]);
  const [realtimeNotice, setRealtimeNotice] = useState<string | null>(null);

  useEffect(() => {
    if (userId) {
      fetchUserSessions();
    }
  }, [userId]);

  // Setup Realtime WebSocket for this participant's sessions and logs
  useEffect(() => {
    if (!userId) return;

    const channel = supabase
      .channel(`user-detail-${userId}`)
      .on(
        'postgres_changes',
        { event: '*', schema: 'public', table: 'blink_sessions', filter: `user_id=eq.${userId}` },
        () => {
          setRealtimeNotice('New session data arrived live!');
          fetchUserSessions();
          setTimeout(() => setRealtimeNotice(null), 3000);
        }
      )
      .on(
        'postgres_changes',
        { event: 'INSERT', schema: 'public', table: 'blink_minute_log' },
        (payload) => {
          const newLog = payload.new as any;
          if (newLog && selectedSession && newLog.session_id === selectedSession.id) {
            setSessionLogs((prev) => [
              ...prev,
              {
                time: format(new Date(newLog.minute_timestamp), 'HH:mm'),
                bpm: Number(newLog.bpm),
              }
            ]);
            setRealtimeNotice(`Live Minute Log: ${Number(newLog.bpm).toFixed(1)} BPM`);
            setTimeout(() => setRealtimeNotice(null), 3000);
          }
        }
      )
      .subscribe();

    return () => {
      supabase.removeChannel(channel);
    };
  }, [userId, selectedSession]);

  async function fetchUserSessions() {
    setLoading(true);
    try {
      const { data } = await supabase
        .from('blink_sessions')
        .select('*')
        .eq('user_id', userId)
        .order('started_at', { ascending: false });

      if (data && data.length > 0) {
        setSessions(data);
        if (!selectedSession) {
          setSelectedSession(data[0]);
          fetchSessionLogs(data[0].id);
        }
      }
    } catch (err) {
      console.error('Error fetching user sessions:', err);
    } finally {
      setLoading(false);
    }
  }

  async function fetchSessionLogs(sessionId: string) {
    try {
      const { data: logs } = await supabase
        .from('blink_minute_log')
        .select('*')
        .eq('session_id', sessionId)
        .order('minute_timestamp', { ascending: true });

      if (logs) {
        const formatted = logs.map(l => ({
          time: format(new Date(l.minute_timestamp), 'HH:mm'),
          bpm: Number(l.bpm),
        }));
        setSessionLogs(formatted);
      }
    } catch (err) {
      console.error('Error fetching session logs:', err);
    }
  }

  if (!userId) {
    return (
      <div className="text-center py-12 text-slate-500">
        Participant ID not specified.{' '}
        <Link href="/users/" className="text-teal-600 underline">
          Return to Users Directory
        </Link>
      </div>
    );
  }

  return (
    <>
      {realtimeNotice && (
        <div className="fixed bottom-6 right-6 z-50 bg-slate-900 text-white px-4 py-3 rounded-2xl shadow-xl border border-slate-800 flex items-center space-x-3 text-sm">
          <Zap className="w-4 h-4 text-emerald-400 animate-pulse" />
          <span>{realtimeNotice}</span>
        </div>
      )}

      <div className="mb-6">
        <Link
          href="/users/"
          className="inline-flex items-center text-sm font-medium text-slate-500 hover:text-slate-900 mb-2"
        >
          <ArrowLeft className="w-4 h-4 mr-1" />
          Back to Participants Directory
        </Link>
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
          <div>
            <h1 className="text-2xl font-bold text-slate-900">Participant Drill-Down</h1>
            <p className="font-mono text-xs text-slate-500 mt-1">UUID: {userId}</p>
          </div>
          <span className="inline-flex items-center px-3 py-1 rounded-full text-xs font-semibold bg-emerald-100 text-emerald-800 self-start sm:self-auto">
            <ShieldCheck className="w-4 h-4 mr-1.5" />
            Consented Research Subject
          </span>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Sessions List */}
        <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
          <h2 className="text-base font-bold text-slate-900 mb-4">Recorded Sessions ({sessions.length})</h2>
          <div className="space-y-3 max-h-[500px] overflow-y-auto pr-1">
            {sessions.map((session) => {
              const isSelected = selectedSession?.id === session.id;
              return (
                <div
                  key={session.id}
                  onClick={() => {
                    setSelectedSession(session);
                    fetchSessionLogs(session.id);
                  }}
                  className={`p-4 rounded-xl border cursor-pointer transition-all ${
                    isSelected
                      ? 'border-teal-500 bg-teal-50/50 shadow-sm'
                      : 'border-slate-200 hover:border-slate-300 bg-white'
                  }`}
                >
                  <div className="flex justify-between items-start mb-1">
                    <span className="text-xs font-semibold text-slate-900">
                      {format(new Date(session.started_at), 'MMM d, yyyy h:mm a')}
                    </span>
                    <span className="text-xs px-2 py-0.5 rounded-full bg-slate-100 text-slate-600 font-medium">
                      {session.monitoring_mode === 'background' ? 'Background' : 'App-Only'}
                    </span>
                  </div>
                  <div className="flex justify-between text-xs text-slate-500 mt-2">
                    <span>Avg: <strong className="text-slate-800">{Number(session.avg_bpm || 0).toFixed(1)} BPM</strong></span>
                    <span>Alerts: <strong className="text-rose-600">{session.alert_count || 0}</strong></span>
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Session Detail & BPM Minute-by-Minute Line Chart */}
        <div className="lg:col-span-2 space-y-6">
          {selectedSession ? (
            <>
              <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
                <div className="flex justify-between items-center mb-2">
                  <h2 className="text-base font-bold text-slate-900">
                    Session BPM Timeline ({format(new Date(selectedSession.started_at), 'MMM d, h:mm a')})
                  </h2>
                  <span className="text-xs text-emerald-600 font-semibold flex items-center">
                    <span className="w-2 h-2 rounded-full bg-emerald-500 mr-1.5 animate-pulse"></span>
                    Live Streaming
                  </span>
                </div>
                <p className="text-xs text-slate-400 mb-4">Minute-by-minute rolling blinks per minute</p>

                <div className="h-64 w-full">
                  {sessionLogs.length > 0 ? (
                    <ResponsiveContainer width="100%" height="100%">
                      <LineChart data={sessionLogs}>
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
                          strokeWidth={2.5}
                          dot={{ r: 3, fill: '#0d9488' }}
                          activeDot={{ r: 6 }}
                        />
                      </LineChart>
                    </ResponsiveContainer>
                  ) : (
                    <div className="flex items-center justify-center h-full text-sm text-slate-400">
                      No minute logs recorded for this session.
                    </div>
                  )}
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <div className="bg-white p-4 rounded-xl border border-slate-200">
                  <span className="text-xs text-slate-500">Average Rate</span>
                  <p className="text-xl font-bold text-slate-900 mt-1">
                    {Number(selectedSession.avg_bpm || 0).toFixed(1)} BPM
                  </p>
                </div>
                <div className="bg-white p-4 rounded-xl border border-slate-200">
                  <span className="text-xs text-slate-500">Minimum Rate</span>
                  <p className="text-xl font-bold text-slate-900 mt-1">
                    {Number(selectedSession.min_bpm || 0).toFixed(1)} BPM
                  </p>
                </div>
                <div className="bg-white p-4 rounded-xl border border-slate-200">
                  <span className="text-xs text-slate-500">Alerts Triggered</span>
                  <p className="text-xl font-bold text-rose-600 mt-1">
                    {selectedSession.alert_count || 0}
                  </p>
                </div>
              </div>
            </>
          ) : (
            <div className="bg-white p-12 rounded-2xl border border-slate-200 text-center text-slate-400 text-sm">
              Select a session to view minute-by-minute timeline
            </div>
          )}
        </div>
      </div>
    </>
  );
}

export default function UserDetailPage() {
  return (
    <div className="min-h-screen bg-slate-50">
      <Navbar />
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <Suspense fallback={<div className="text-center py-12 text-slate-400">Loading participant details...</div>}>
          <UserDetailContent />
        </Suspense>
      </main>
    </div>
  );
}
