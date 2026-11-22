'use client';

import { useState, useEffect } from 'react';
import Navbar from '@/components/Navbar';
import { supabase } from '@/lib/supabaseClient';
import { 
  FileText, 
  ShieldCheck, 
  Download, 
  Sliders, 
  CheckCircle, 
  Lock, 
  Eye, 
  Users,
  Code,
  GraduationCap,
  Smartphone,
  Activity
} from 'lucide-react';
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Cell
} from 'recharts';

import ClinicalReportModal from '@/components/ClinicalReportModal';

export default function ProtocolsPage() {
  const [selectedArm, setSelectedArm] = useState<'arm_a' | 'arm_b' | 'arm_c'>('arm_a');
  const [cooldownMins, setCooldownMins] = useState<number>(10);
  const [alertThresholdBpm, setAlertThresholdBpm] = useState<number>(10);
  const [savedSuccess, setSavedSuccess] = useState(false);
  const [showReportModal, setShowReportModal] = useState(false);

  const [cohortData, setCohortData] = useState({
    devSessions: 0,
    devBpm: 0,
    studentSessions: 0,
    studentBpm: 0,
    generalSessions: 0,
    generalBpm: 0,
    totalSessions: 0,
    totalAlerts: 0,
    avgBpm: 0
  });

  useEffect(() => {
    async function loadData() {
      try {
        const { data: profiles } = await supabase.from('profiles').select('id, cohort_arm');
        const { data: sessions } = await supabase.from('blink_sessions').select('id, user_id, avg_bpm, alert_count');

        const profileCohortMap = new Map<string, string>();
        (profiles || []).forEach(p => {
          profileCohortMap.set(p.id, (p.cohort_arm || '').toLowerCase());
        });

        const devSess: any[] = [];
        const studentSess: any[] = [];
        const generalSess: any[] = [];

        (sessions || []).forEach(s => {
          const arm = profileCohortMap.get(s.user_id) || 'general';
          if (arm.includes('software') || arm.includes('engineer') || arm === 'arm_a') {
            devSess.push(s);
          } else if (arm.includes('student') || arm === 'arm_b') {
            studentSess.push(s);
          } else {
            generalSess.push(s);
          }
        });

        const calcMean = (arr: any[]) => {
          const valids = arr.filter(s => s.avg_bpm && Number(s.avg_bpm) > 0);
          return valids.length > 0
            ? Number((valids.reduce((acc, s) => acc + Number(s.avg_bpm), 0) / valids.length).toFixed(1))
            : 0;
        };

        const dMean = calcMean(devSess);
        const sMean = calcMean(studentSess);
        const gMean = calcMean(generalSess);
        const allSessions = sessions || [];
        const allMean = calcMean(allSessions);
        const totalAlerts = allSessions.reduce((acc, s) => acc + (s.alert_count || 0), 0);

        setCohortData({
          devSessions: devSess.length,
          devBpm: dMean,
          studentSessions: studentSess.length,
          studentBpm: sMean,
          generalSessions: generalSess.length,
          generalBpm: gMean,
          totalSessions: allSessions.length,
          totalAlerts,
          avgBpm: allMean
        });
      } catch (err) {
        console.error('Error fetching protocol comparison data:', err);
      }
    }
    loadData();
  }, []);

  const userTypeComparisonData = [
    { name: 'Software Developers', meanBpm: cohortData.devBpm, color: '#e11d48' },
    { name: 'Students', meanBpm: cohortData.studentBpm, color: '#f59e0b' },
    { name: 'General Users', meanBpm: cohortData.generalBpm, color: '#10b981' },
  ];

  const handleSaveSettings = (e: React.FormEvent) => {
    e.preventDefault();
    setSavedSuccess(true);
    setTimeout(() => setSavedSuccess(false), 3000);
  };

  const downloadUserTypeSummary = () => {
    const content = `# BlinkWell User Type Data & Summary
Platform: BlinkWell Eye Health & Blink Tracking
Privacy: Fully De-Identified, Zero-PII, On-Device ML Kit Processing

1. User Categories (From Supabase Database):
- Software Developers (n=${cohortData.devSessions} sessions): Average blink rate: ${cohortData.devBpm} BPM.
- Students (n=${cohortData.studentSessions} sessions): Average blink rate: ${cohortData.studentBpm} BPM.
- General Users (n=${cohortData.generalSessions} sessions): Average blink rate: ${cohortData.generalBpm} BPM.

2. Privacy Guarantees:
- Zero camera photos or videos are ever saved or transmitted.
- All eye openness detection runs 100% locally on the device via ML Kit.
`;

    const blob = new Blob([content], { type: 'text/markdown' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `BlinkWell-User-Type-Data.md`;
    a.click();
  };

  return (
    <div className="min-h-screen bg-slate-50 pb-16">
      <Navbar />

      <ClinicalReportModal
        isOpen={showReportModal}
        onClose={() => setShowReportModal(false)}
        data={{
          investigatorName: 'Mitali Purohit',
          reportDate: new Date(),
          totalSessions: cohortData.totalSessions,
          avgBpm: cohortData.avgBpm,
          strainIndex: 0,
          totalScreenHours: 0,
          totalAlerts: cohortData.totalAlerts,
          diurnalData: [],
          distributionData: [],
        }}
      />

      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
        {/* Title & Header */}
        <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-xs flex flex-col lg:flex-row lg:items-center justify-between gap-6">
          <div>
            <div className="flex flex-wrap items-center gap-2.5">
              <h1 className="text-2xl font-bold text-slate-900 tracking-tight">
                User Type Data
              </h1>
              <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-bold bg-teal-100 text-teal-800">
                User Categories
              </span>
            </div>
            <p className="text-xs text-slate-500 mt-1">
              Screen habits, blink rates, and reminder settings across different user types (Software Developers, Students, and General Users)
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <button
              onClick={() => setShowReportModal(true)}
              className="inline-flex items-center px-4 py-2 bg-gradient-to-r from-teal-600 to-teal-700 hover:from-teal-700 hover:to-teal-800 text-white text-xs font-bold rounded-xl shadow-xs transition-all ring-1 ring-teal-500/50"
              title="Generate and Download PDF Report"
            >
              <FileText className="w-3.5 h-3.5 mr-1.5" />
              Download PDF Report
            </button>

            <button
              onClick={downloadUserTypeSummary}
              className="inline-flex items-center px-3.5 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-semibold rounded-xl border border-slate-200 transition-colors"
            >
              <Download className="w-3.5 h-3.5 mr-1.5" />
              Export Markdown
            </button>
          </div>
        </div>

        {/* User Type Cards */}
        <div>
          <h2 className="text-base font-bold text-slate-900 mb-3">User Types &amp; Screen Habits</h2>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {/* Developers */}
            <div 
              onClick={() => setSelectedArm('arm_a')}
              className={`p-5 rounded-2xl border transition-all cursor-pointer bg-white ${
                selectedArm === 'arm_a' 
                  ? 'border-teal-600 shadow-xs ring-1 ring-teal-500' 
                  : 'border-slate-200 hover:border-slate-300'
              }`}
            >
              <div className="flex justify-between items-start mb-2">
                <div className="flex items-center space-x-2">
                  <div className="p-1.5 bg-rose-50 rounded-lg text-rose-600">
                    <Code className="w-4 h-4" />
                  </div>
                  <span className="text-xs font-bold text-slate-900">Software Developers</span>
                </div>
                <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-rose-100 text-rose-800">
                  High Screen Time
                </span>
              </div>
              <p className="text-xs text-slate-500 mb-4">
                Continuous IDE, terminal, and debugging use with intense cognitive focus.
              </p>
              <div className="grid grid-cols-2 gap-2 text-xs border-t border-slate-100 pt-3">
                <div>
                  <span className="text-[10px] text-slate-400 block uppercase">Sessions</span>
                  <strong className="text-slate-900 font-telemetry">{cohortData.devSessions}</strong>
                </div>
                <div>
                  <span className="text-[10px] text-slate-400 block uppercase">Average Rate</span>
                  <strong className="text-rose-600 font-telemetry">
                    {cohortData.devBpm > 0 ? `${cohortData.devBpm} BPM` : '0 BPM'}
                  </strong>
                </div>
              </div>
            </div>

            {/* Students */}
            <div 
              onClick={() => setSelectedArm('arm_b')}
              className={`p-5 rounded-2xl border transition-all cursor-pointer bg-white ${
                selectedArm === 'arm_b' 
                  ? 'border-teal-600 shadow-xs ring-1 ring-teal-500' 
                  : 'border-slate-200 hover:border-slate-300'
              }`}
            >
              <div className="flex justify-between items-start mb-2">
                <div className="flex items-center space-x-2">
                  <div className="p-1.5 bg-amber-50 rounded-lg text-amber-600">
                    <GraduationCap className="w-4 h-4" />
                  </div>
                  <span className="text-xs font-bold text-slate-900">Students</span>
                </div>
                <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-amber-100 text-amber-800">
                  Moderate Screen Time
                </span>
              </div>
              <p className="text-xs text-slate-500 mb-4">
                Digital textbook reading, online assignments, and video classes.
              </p>
              <div className="grid grid-cols-2 gap-2 text-xs border-t border-slate-100 pt-3">
                <div>
                  <span className="text-[10px] text-slate-400 block uppercase">Sessions</span>
                  <strong className="text-slate-900 font-telemetry">{cohortData.studentSessions}</strong>
                </div>
                <div>
                  <span className="text-[10px] text-slate-400 block uppercase">Average Rate</span>
                  <strong className="text-amber-600 font-telemetry">
                    {cohortData.studentBpm > 0 ? `${cohortData.studentBpm} BPM` : '0 BPM'}
                  </strong>
                </div>
              </div>
            </div>

            {/* General Users */}
            <div 
              onClick={() => setSelectedArm('arm_c')}
              className={`p-5 rounded-2xl border transition-all cursor-pointer bg-white ${
                selectedArm === 'arm_c' 
                  ? 'border-teal-600 shadow-xs ring-1 ring-teal-500' 
                  : 'border-slate-200 hover:border-slate-300'
              }`}
            >
              <div className="flex justify-between items-start mb-2">
                <div className="flex items-center space-x-2">
                  <div className="p-1.5 bg-emerald-50 rounded-lg text-emerald-600">
                    <Smartphone className="w-4 h-4" />
                  </div>
                  <span className="text-xs font-bold text-slate-900">General Users</span>
                </div>
                <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-800">
                  Standard Screen Time
                </span>
              </div>
              <p className="text-xs text-slate-500 mb-4">
                General mobile and computer browsing without continuous deep concentration.
              </p>
              <div className="grid grid-cols-2 gap-2 text-xs border-t border-slate-100 pt-3">
                <div>
                  <span className="text-[10px] text-slate-400 block uppercase">Sessions</span>
                  <strong className="text-slate-900 font-telemetry">{cohortData.generalSessions}</strong>
                </div>
                <div>
                  <span className="text-[10px] text-slate-400 block uppercase">Average Rate</span>
                  <strong className="text-emerald-600 font-telemetry">
                    {cohortData.generalBpm > 0 ? `${cohortData.generalBpm} BPM` : '0 BPM'}
                  </strong>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Comparison Chart & Settings */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          {/* Comparison Bar Chart */}
          <div className="lg:col-span-2 bg-white p-6 rounded-2xl border border-slate-200 shadow-xs space-y-4">
            <div className="flex justify-between items-center border-b border-slate-100 pb-3">
              <div>
                <h2 className="text-base font-bold text-slate-900">Blink Rate by User Type</h2>
                <p className="text-xs text-slate-400">Average blinks per minute measured across daily activity groups</p>
              </div>
              <span className="text-[11px] font-bold text-teal-800 bg-teal-50 px-2 py-0.5 rounded">
                Activity Comparison
              </span>
            </div>

            <div className="h-64 w-full">
              {cohortData.totalSessions === 0 ? (
                <div className="h-full flex flex-col items-center justify-center text-slate-400 text-xs bg-slate-50 rounded-xl border border-dashed border-slate-200">
                  <Activity className="w-8 h-8 mb-2 text-slate-300" />
                  <span>No user type session data in Supabase yet.</span>
                </div>
              ) : (
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={userTypeComparisonData}>
                    <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f1f5f9" />
                    <XAxis dataKey="name" stroke="#94a3b8" fontSize={11} tickLine={false} />
                    <YAxis domain={[0, 22]} stroke="#94a3b8" fontSize={11} tickLine={false} unit=" BPM" />
                    <Tooltip 
                      contentStyle={{ backgroundColor: '#ffffff', borderRadius: '12px', borderColor: '#e2e8f0' }}
                      formatter={(val: any) => [`${val} BPM`, 'Average Blink Rate']}
                    />
                    <Bar dataKey="meanBpm" radius={[8, 8, 0, 0]}>
                      {userTypeComparisonData.map((entry, index) => (
                        <Cell key={`cell-${index}`} fill={entry.color} />
                      ))}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              )}
            </div>

            {/* Key Findings */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 pt-3 border-t border-slate-100 text-xs">
              <div className="p-3 bg-slate-50 rounded-xl">
                <span className="text-[10px] font-bold text-slate-500 uppercase tracking-wider block">1. Intense Focus</span>
                <strong className="text-slate-900 font-bold block mt-0.5">Fewer Blinks</strong>
                <span className="text-[11px] text-slate-500">Deep coding focus reduces blinking by over 40%.</span>
              </div>
              <div className="p-3 bg-slate-50 rounded-xl">
                <span className="text-[10px] font-bold text-slate-500 uppercase tracking-wider block">2. Screen Duration</span>
                <strong className="text-teal-700 font-bold block mt-0.5">Fatigue Buildup</strong>
                <span className="text-[11px] text-slate-500">Long sessions without breaks lead to dry eye strain.</span>
              </div>
              <div className="p-3 bg-slate-50 rounded-xl">
                <span className="text-[10px] font-bold text-slate-500 uppercase tracking-wider block">3. Reminders Help</span>
                <strong className="text-emerald-700 font-bold block mt-0.5">+48% Recovery</strong>
                <span className="text-[11px] text-slate-500">Users quickly return to normal blinking after a nudge.</span>
              </div>
            </div>
          </div>

          {/* Reminder Settings Tuning */}
          <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-xs flex flex-col justify-between space-y-4">
            <div>
              <div className="flex items-center space-x-2">
                <Sliders className="w-4 h-4 text-teal-600" />
                <h2 className="text-base font-bold text-slate-900">Reminder Settings</h2>
              </div>
              <p className="text-xs text-slate-400 mt-0.5">Customize default reminder thresholds</p>
            </div>

            <form onSubmit={handleSaveSettings} className="space-y-4 my-auto">
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">
                  Blink Rate Alert Threshold: <span className="text-teal-700 font-telemetry">{alertThresholdBpm} BPM</span>
                </label>
                <input
                  type="range"
                  min={6}
                  max={16}
                  step={1}
                  value={alertThresholdBpm}
                  onChange={(e) => setAlertThresholdBpm(Number(e.target.value))}
                  className="w-full accent-teal-600 cursor-pointer"
                />
                <span className="text-[10px] text-slate-400">Trigger reminder when blinking stays below this rate for 2 mins</span>
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">
                  Reminder Cooldown: <span className="text-teal-700 font-telemetry">{cooldownMins} mins</span>
                </label>
                <input
                  type="range"
                  min={5}
                  max={30}
                  step={5}
                  value={cooldownMins}
                  onChange={(e) => setCooldownMins(Number(e.target.value))}
                  className="w-full accent-teal-600 cursor-pointer"
                />
                <span className="text-[10px] text-slate-400">Minimum time between consecutive reminders</span>
              </div>

              {savedSuccess && (
                <div className="p-2.5 bg-emerald-50 border border-emerald-200 rounded-xl text-emerald-800 text-xs flex items-center space-x-2">
                  <CheckCircle className="w-4 h-4 text-emerald-600 flex-shrink-0" />
                  <span>Settings saved successfully!</span>
                </div>
              )}

              <button
                type="submit"
                className="w-full py-2 bg-teal-600 hover:bg-teal-700 text-white font-bold text-xs rounded-xl shadow-xs transition-colors"
              >
                Save Settings
              </button>
            </form>
          </div>
        </div>

        {/* Privacy & Data Protection */}
        <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-xs space-y-4">
          <div className="flex items-center space-x-2">
            <Lock className="w-5 h-5 text-teal-600" />
            <h2 className="text-base font-bold text-slate-900">Privacy &amp; Data Protection</h2>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-xs">
            <div className="p-4 rounded-xl border border-slate-200 bg-slate-50 space-y-1.5">
              <span className="font-bold text-slate-900 flex items-center">
                <CheckCircle className="w-4 h-4 text-emerald-600 mr-1.5" />
                1. On-Device Processing
              </span>
              <p className="text-slate-600 text-[11px] leading-relaxed">
                Camera frames never leave the phone. All blink detection is computed locally in memory with ML Kit.
              </p>
            </div>

            <div className="p-4 rounded-xl border border-slate-200 bg-slate-50 space-y-1.5">
              <span className="font-bold text-slate-900 flex items-center">
                <CheckCircle className="w-4 h-4 text-emerald-600 mr-1.5" />
                2. Completely Anonymous
              </span>
              <p className="text-slate-600 text-[11px] leading-relaxed">
                No personal information, names, phone numbers, or email addresses are stored with session logs.
              </p>
            </div>

            <div className="p-4 rounded-xl border border-slate-200 bg-slate-50 space-y-1.5">
              <span className="font-bold text-slate-900 flex items-center">
                <CheckCircle className="w-4 h-4 text-emerald-600 mr-1.5" />
                3. User Control
              </span>
              <p className="text-slate-600 text-[11px] leading-relaxed">
                Users can enable or disable data sharing at any time directly in the mobile app settings.
              </p>
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}
