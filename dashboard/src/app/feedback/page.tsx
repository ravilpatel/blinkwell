'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import Navbar from '@/components/Navbar';
import { supabase } from '@/lib/supabaseClient';
import { 
  MessageSquare, 
  Download, 
  RefreshCw, 
  Search, 
  Smile, 
  Sparkles, 
  Clock, 
  HeartHandshake, 
  Sliders, 
  Filter, 
  CheckCircle2, 
  Layers, 
  Calendar, 
  User, 
  Quote
} from 'lucide-react';
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  PieChart,
  Pie,
  Cell
} from 'recharts';
import { format } from 'date-fns';

interface FeedbackItem {
  id: string;
  user_id: string | null;
  usage_duration: string;
  comfort_improvement: string;
  reminder_comfort: string;
  preferred_mode: string;
  comments: string | null;
  created_at: string;
}

export default function FeedbackPortalPage() {
  const router = useRouter();
  const [feedbackList, setFeedbackList] = useState<FeedbackItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedDuration, setSelectedDuration] = useState('all');
  const [selectedComfort, setSelectedComfort] = useState('all');
  const [selectedMode, setSelectedMode] = useState('all');

  useEffect(() => {
    checkAuthAndFetchFeedback();

    // Supabase Realtime channel for live incoming feedback
    const channel = supabase
      .channel('dashboard-realtime-feedback')
      .on(
        'postgres_changes',
        { event: '*', schema: 'public', table: 'feedback' },
        () => {
          fetchFeedbackData();
        }
      )
      .subscribe();

    return () => {
      supabase.removeChannel(channel);
    };
  }, []);

  async function checkAuthAndFetchFeedback() {
    setLoading(true);
    try {
      const { data: { session } } = await supabase.auth.getSession();
      if (!session) {
        router.push('/login/');
        return;
      }
      await fetchFeedbackData();
    } catch (err) {
      console.error('Auth error in Feedback portal:', err);
    } finally {
      setLoading(false);
    }
  }

  async function fetchFeedbackData() {
    try {
      const { data, error } = await supabase
        .from('feedback')
        .select('*')
        .order('created_at', { ascending: false });

      if (error) {
        console.error('Error fetching feedback from Supabase:', error);
        setFeedbackList([]);
      } else {
        setFeedbackList(data || []);
      }
    } catch (err) {
      console.error('Error fetching feedback:', err);
      setFeedbackList([]);
    }
  }

  const handleManualRefresh = async () => {
    setRefreshing(true);
    await fetchFeedbackData();
    setTimeout(() => setRefreshing(false), 500);
  };

  // Filtered feedback
  const filteredFeedback = feedbackList.filter((item) => {
    const matchesSearch = 
      !searchTerm ||
      (item.comments && item.comments.toLowerCase().includes(searchTerm.toLowerCase())) ||
      (item.user_id && item.user_id.toLowerCase().includes(searchTerm.toLowerCase()));

    const matchesDuration = 
      selectedDuration === 'all' || item.usage_duration === selectedDuration;

    const matchesComfort = 
      selectedComfort === 'all' || item.comfort_improvement === selectedComfort;

    const matchesMode = 
      selectedMode === 'all' || item.preferred_mode === selectedMode;

    return matchesSearch && matchesDuration && matchesComfort && matchesMode;
  });

  // Calculate Statistics
  const totalResponses = feedbackList.length;
  
  const positiveComfortCount = feedbackList.filter(f => 
    f.comfort_improvement.includes('Significant') || f.comfort_improvement.includes('Moderate')
  ).length;
  const comfortImprovementRate = totalResponses > 0 
    ? Math.round((positiveComfortCount / totalResponses) * 100) 
    : 0;

  const comfortableRemindersCount = feedbackList.filter(f => 
    f.reminder_comfort.includes('gentle') || f.reminder_comfort.includes('Helpful')
  ).length;
  const reminderAcceptanceRate = totalResponses > 0 
    ? Math.round((comfortableRemindersCount / totalResponses) * 100) 
    : 0;

  const burstModeUsers = feedbackList.filter(f => f.preferred_mode.includes('Burst')).length;
  const burstModePct = totalResponses > 0 
    ? Math.round((burstModeUsers / totalResponses) * 100) 
    : 0;

  // Chart 1: Comfort Breakdown
  const comfortBuckets: Record<string, number> = {
    'Significant': 0,
    'Moderate': 0,
    'Subtle': 0,
    'Too early': 0,
  };
  feedbackList.forEach(f => {
    if (f.comfort_improvement.includes('Significant')) comfortBuckets['Significant']++;
    else if (f.comfort_improvement.includes('Moderate')) comfortBuckets['Moderate']++;
    else if (f.comfort_improvement.includes('Subtle')) comfortBuckets['Subtle']++;
    else comfortBuckets['Too early']++;
  });

  const comfortChartData = [
    { name: 'Significant', count: comfortBuckets['Significant'], fill: '#0d9488' },
    { name: 'Moderate', count: comfortBuckets['Moderate'], fill: '#14b8a6' },
    { name: 'Subtle', count: comfortBuckets['Subtle'], fill: '#f59e0b' },
    { name: 'Too Early', count: comfortBuckets['Too early'], fill: '#94a3b8' },
  ];

  // Chart 2: Usage Duration Breakdown
  const durationBuckets: Record<string, number> = {
    'First Day': 0,
    '1 Week': 0,
    '1-3 Weeks': 0,
    '1+ Months': 0,
  };
  feedbackList.forEach(f => {
    if (f.usage_duration.includes('First day')) durationBuckets['First Day']++;
    else if (f.usage_duration.includes('2 – 6 days')) durationBuckets['1 Week']++;
    else if (f.usage_duration.includes('1 – 3 weeks')) durationBuckets['1-3 Weeks']++;
    else durationBuckets['1+ Months']++;
  });

  const durationChartData = [
    { duration: 'First Day', count: durationBuckets['First Day'] },
    { duration: '1 Week', count: durationBuckets['1 Week'] },
    { duration: '1-3 Weeks', count: durationBuckets['1-3 Weeks'] },
    { duration: '1+ Months', count: durationBuckets['1+ Months'] },
  ];

  // CSV Export
  const exportToCSV = () => {
    const headers = ['ID', 'User ID', 'Created At', 'Usage Duration', 'Comfort Improvement', 'Reminder Comfort', 'Preferred Mode', 'Comments'];
    const rows = filteredFeedback.map(f => [
      f.id,
      f.user_id || 'anonymous',
      f.created_at,
      `"${f.usage_duration.replace(/"/g, '""')}"`,
      `"${f.comfort_improvement.replace(/"/g, '""')}"`,
      `"${f.reminder_comfort.replace(/"/g, '""')}"`,
      `"${f.preferred_mode.replace(/"/g, '""')}"`,
      `"${(f.comments || '').replace(/"/g, '""')}"`
    ]);

    const csvContent = '\uFEFF' + [headers.join(','), ...rows.map(r => r.join(','))].join('\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', `BlinkWell-Feedback-Export-${format(new Date(), 'yyyyMMdd-HHmm')}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const getComfortBadge = (comfort: string) => {
    if (comfort.includes('Significant')) {
      return <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-emerald-100 text-emerald-800 border border-emerald-200">🌟 Significant</span>;
    }
    if (comfort.includes('Moderate')) {
      return <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-teal-100 text-teal-800 border border-teal-200">👍 Moderate</span>;
    }
    if (comfort.includes('Subtle')) {
      return <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-amber-100 text-amber-800 border border-amber-200">😐 Subtle</span>;
    }
    return <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-slate-100 text-slate-700 border border-slate-200">⏳ Too Early</span>;
  };

  return (
    <div className="min-h-screen bg-slate-50 pb-16">
      <Navbar />

      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-8 space-y-8">
        {/* Header Section */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 bg-white p-6 rounded-2xl border border-slate-200 shadow-xs">
          <div>
            <div className="flex items-center space-x-3">
              <div className="p-2.5 bg-teal-50 border border-teal-200 rounded-xl text-teal-600">
                <MessageSquare className="w-6 h-6" />
              </div>
              <div>
                <h1 className="text-xl sm:text-2xl font-bold text-slate-900 tracking-tight">
                  User Feedback & Outcomes
                </h1>
                <p className="text-sm text-slate-500">
                  Direct participant feedback, eye comfort ratings, and app suggestions collected from mobile devices.
                </p>
              </div>
            </div>
          </div>

          <div className="flex items-center space-x-3">
            <button
              onClick={handleManualRefresh}
              disabled={refreshing}
              className="inline-flex items-center px-3.5 py-2 border border-slate-200 shadow-xs text-xs font-semibold rounded-xl text-slate-700 bg-white hover:bg-slate-50 focus:outline-hidden disabled:opacity-50 transition-colors"
            >
              <RefreshCw className={`w-3.5 h-3.5 mr-2 ${refreshing ? 'animate-spin text-teal-600' : 'text-slate-500'}`} />
              {refreshing ? 'Refreshing...' : 'Refresh'}
            </button>
            <button
              onClick={exportToCSV}
              className="inline-flex items-center px-4 py-2 bg-teal-600 border border-transparent shadow-xs text-xs font-semibold rounded-xl text-white hover:bg-teal-700 focus:outline-hidden transition-colors"
            >
              <Download className="w-3.5 h-3.5 mr-2" />
              Export CSV
            </button>
          </div>
        </div>

        {/* Metric Overview Cards */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
          <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-xs">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Total Responses</span>
              <div className="p-2 bg-teal-50 rounded-lg text-teal-600">
                <MessageSquare className="w-4 h-4" />
              </div>
            </div>
            <div className="mt-3 flex items-baseline justify-between">
              <span className="text-2xl font-extrabold text-slate-900">{totalResponses}</span>
              <span className="text-xs font-semibold text-emerald-600 bg-emerald-50 px-2 py-0.5 rounded-full">
                Active Study
              </span>
            </div>
            <p className="mt-1 text-xs text-slate-500">Participant evaluations logged</p>
          </div>

          <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-xs">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Comfort Improvement</span>
              <div className="p-2 bg-emerald-50 rounded-lg text-emerald-600">
                <Smile className="w-4 h-4" />
              </div>
            </div>
            <div className="mt-3 flex items-baseline justify-between">
              <span className="text-2xl font-extrabold text-slate-900">{comfortImprovementRate}%</span>
              <span className="text-xs font-semibold text-emerald-600 bg-emerald-50 px-2 py-0.5 rounded-full">
                Positive
              </span>
            </div>
            <p className="mt-1 text-xs text-slate-500">Reported noticeable or significant relief</p>
          </div>

          <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-xs">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Reminder Acceptance</span>
              <div className="p-2 bg-indigo-50 rounded-lg text-indigo-600">
                <Sparkles className="w-4 h-4" />
              </div>
            </div>
            <div className="mt-3 flex items-baseline justify-between">
              <span className="text-2xl font-extrabold text-slate-900">{reminderAcceptanceRate}%</span>
              <span className="text-xs font-semibold text-indigo-600 bg-indigo-50 px-2 py-0.5 rounded-full">
                Well-Timed
              </span>
            </div>
            <p className="mt-1 text-xs text-slate-500">Rated haptic cues gentle & helpful</p>
          </div>

          <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-xs">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Burst Mode Popularity</span>
              <div className="p-2 bg-amber-50 rounded-lg text-amber-600">
                <Clock className="w-4 h-4" />
              </div>
            </div>
            <div className="mt-3 flex items-baseline justify-between">
              <span className="text-2xl font-extrabold text-slate-900">{burstModePct}%</span>
              <span className="text-xs font-semibold text-amber-700 bg-amber-50 px-2 py-0.5 rounded-full">
                Top Pick
              </span>
            </div>
            <p className="mt-1 text-xs text-slate-500">Prefer 5-min micro-check sessions</p>
          </div>
        </div>

        {/* Charts Section */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* Comfort Distribution */}
          <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-xs">
            <div className="flex items-center justify-between mb-4">
              <div>
                <h2 className="text-base font-bold text-slate-900">Perceived Eye Comfort Relief</h2>
                <p className="text-xs text-slate-500">Patient-Reported Outcomes (PRO) after using BlinkWell</p>
              </div>
              <span className="p-1.5 bg-teal-50 text-teal-600 rounded-lg">
                <HeartHandshake className="w-4 h-4" />
              </span>
            </div>
            {totalResponses === 0 ? (
              <div className="h-64 flex flex-col items-center justify-center text-slate-400 text-xs bg-slate-50/50 rounded-xl border border-dashed border-slate-200">
                <HeartHandshake className="w-8 h-8 mb-2 text-slate-300" />
                <span>No feedback responses recorded in Supabase yet.</span>
              </div>
            ) : (
              <div className="h-64">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={comfortChartData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                    <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f1f5f9" />
                    <XAxis dataKey="name" tick={{ fontSize: 11, fill: '#64748b' }} axisLine={false} tickLine={false} />
                    <YAxis tick={{ fontSize: 11, fill: '#64748b' }} axisLine={false} tickLine={false} allowDecimals={false} />
                    <Tooltip 
                      contentStyle={{ borderRadius: '12px', border: '1px solid #e2e8f0', boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)' }}
                    />
                    <Bar dataKey="count" radius={[6, 6, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}
          </div>

          {/* Usage Duration Distribution */}
          <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-xs">
            <div className="flex items-center justify-between mb-4">
              <div>
                <h2 className="text-base font-bold text-slate-900">Participant App Tenure</h2>
                <p className="text-xs text-slate-500">Duration participants have utilized BlinkWell</p>
              </div>
              <span className="p-1.5 bg-indigo-50 text-indigo-600 rounded-lg">
                <Clock className="w-4 h-4" />
              </span>
            </div>
            {totalResponses === 0 ? (
              <div className="h-64 flex flex-col items-center justify-center text-slate-400 text-xs bg-slate-50/50 rounded-xl border border-dashed border-slate-200">
                <Clock className="w-8 h-8 mb-2 text-slate-300" />
                <span>No feedback responses recorded in Supabase yet.</span>
              </div>
            ) : (
              <div className="h-64">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={durationChartData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                    <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f1f5f9" />
                    <XAxis dataKey="duration" tick={{ fontSize: 11, fill: '#64748b' }} axisLine={false} tickLine={false} />
                    <YAxis tick={{ fontSize: 11, fill: '#64748b' }} axisLine={false} tickLine={false} allowDecimals={false} />
                    <Tooltip 
                      contentStyle={{ borderRadius: '12px', border: '1px solid #e2e8f0', boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)' }}
                    />
                    <Bar dataKey="count" fill="#6366f1" radius={[6, 6, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}
          </div>
        </div>

        {/* Filters and Search Bar */}
        <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-xs space-y-4">
          <div className="flex flex-col sm:flex-row gap-4 justify-between items-center">
            {/* Search Input */}
            <div className="relative w-full sm:w-80">
              <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                placeholder="Search comments or user IDs..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="w-full pl-9 pr-4 py-2 border border-slate-200 rounded-xl text-xs focus:outline-hidden focus:ring-2 focus:ring-teal-500 focus:border-teal-500 bg-slate-50/50"
              />
            </div>

            {/* Filter Dropdowns */}
            <div className="flex flex-wrap items-center gap-2.5 w-full sm:w-auto">
              <div className="flex items-center space-x-1.5 text-xs text-slate-500">
                <Filter className="w-3.5 h-3.5" />
                <span className="font-semibold">Filters:</span>
              </div>

              {/* Usage Duration Filter */}
              <select
                value={selectedDuration}
                onChange={(e) => setSelectedDuration(e.target.value)}
                className="px-3 py-1.5 text-xs bg-white border border-slate-200 rounded-lg text-slate-700 font-medium focus:ring-teal-500 focus:border-teal-500"
              >
                <option value="all">All Durations</option>
                <option value="First day / Just started">First Day</option>
                <option value="2 – 6 days (1 week)">1 Week</option>
                <option value="1 – 3 weeks">1 – 3 Weeks</option>
                <option value="1 month or more">1+ Month</option>
              </select>

              {/* Comfort Filter */}
              <select
                value={selectedComfort}
                onChange={(e) => setSelectedComfort(e.target.value)}
                className="px-3 py-1.5 text-xs bg-white border border-slate-200 rounded-lg text-slate-700 font-medium focus:ring-teal-500 focus:border-teal-500"
              >
                <option value="all">All Comfort Levels</option>
                <option value="🌟 Significant (eyes feel fresher & less dry)">Significant Relief</option>
                <option value="👍 Moderate (blinking more frequently)">Moderate Improvement</option>
                <option value="😐 Subtle / Little change so far">Subtle Change</option>
                <option value="⏳ Too early to tell">Too Early to Tell</option>
              </select>

              {/* Mode Filter */}
              <select
                value={selectedMode}
                onChange={(e) => setSelectedMode(e.target.value)}
                className="px-3 py-1.5 text-xs bg-white border border-slate-200 rounded-lg text-slate-700 font-medium focus:ring-teal-500 focus:border-teal-500"
              >
                <option value="all">All Modes</option>
                <option value="⏱️ 5-Minute Eye Check (Burst Mode)">5-Min Burst Mode</option>
                <option value="🔄 All-Day Monitoring Mode">All-Day Monitoring</option>
                <option value="⚖️ Both equally">Both Equally</option>
              </select>
            </div>
          </div>
        </div>

        {/* Feedback Responses Feed */}
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-bold text-slate-900">
              Submitted Feedback ({filteredFeedback.length})
            </h2>
            <span className="text-xs text-slate-500">
              Ordered by newest submission first
            </span>
          </div>

          {filteredFeedback.length === 0 ? (
            <div className="bg-white p-12 text-center rounded-2xl border border-slate-200">
              <MessageSquare className="w-10 h-10 text-slate-300 mx-auto mb-3" />
              <h3 className="text-sm font-semibold text-slate-700">No matching feedback found</h3>
              <p className="text-xs text-slate-500 mt-1">
                Try clearing your search filters or submitting feedback from the mobile app.
              </p>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {filteredFeedback.map((item) => (
                <div 
                  key={item.id} 
                  className="bg-white p-5 rounded-2xl border border-slate-200 shadow-xs hover:border-teal-200 transition-all flex flex-col justify-between"
                >
                  <div className="space-y-3.5">
                    {/* Top Row: User + Date */}
                    <div className="flex items-center justify-between border-b border-slate-100 pb-3">
                      <div className="flex items-center space-x-2">
                        <div className="w-7 h-7 rounded-full bg-teal-100 text-teal-700 flex items-center justify-center font-bold text-xs">
                          <User className="w-3.5 h-3.5" />
                        </div>
                        <div>
                          <span className="text-xs font-semibold text-slate-800 block">
                            {item.user_id ? `User ${item.user_id.substring(0, 8)}...` : 'Anonymous Participant'}
                          </span>
                          <span className="text-[10px] text-slate-400">
                            {item.created_at ? format(new Date(item.created_at), 'MMM d, yyyy • h:mm a') : 'Recent'}
                          </span>
                        </div>
                      </div>

                      {getComfortBadge(item.comfort_improvement)}
                    </div>

                    {/* Metadata Badges */}
                    <div className="grid grid-cols-2 gap-2 text-xs">
                      <div className="bg-slate-50 p-2.5 rounded-xl border border-slate-100">
                        <span className="text-[10px] uppercase font-bold text-slate-400 block mb-0.5">App Tenure</span>
                        <span className="font-semibold text-slate-700 block truncate" title={item.usage_duration}>
                          {item.usage_duration}
                        </span>
                      </div>

                      <div className="bg-slate-50 p-2.5 rounded-xl border border-slate-100">
                        <span className="text-[10px] uppercase font-bold text-slate-400 block mb-0.5">Preferred Mode</span>
                        <span className="font-semibold text-slate-700 block truncate" title={item.preferred_mode}>
                          {item.preferred_mode}
                        </span>
                      </div>
                    </div>

                    <div className="bg-slate-50/80 px-3 py-2 rounded-xl border border-slate-100 text-xs">
                      <span className="text-[10px] uppercase font-bold text-slate-400 block mb-0.5">Reminder Comfort</span>
                      <span className="font-medium text-slate-700">
                        {item.reminder_comfort}
                      </span>
                    </div>

                    {/* User Comments / Quotation */}
                    {item.comments ? (
                      <div className="bg-teal-50/60 p-3.5 rounded-xl border border-teal-100/70 relative">
                        <Quote className="w-3.5 h-3.5 text-teal-400 absolute top-2.5 left-2.5 opacity-60" />
                        <p className="text-xs text-slate-800 italic pl-4 font-normal leading-relaxed">
                          &ldquo;{item.comments}&rdquo;
                        </p>
                      </div>
                    ) : (
                      <p className="text-[11px] text-slate-400 italic">
                        No additional written comments provided.
                      </p>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </main>
    </div>
  );
}
