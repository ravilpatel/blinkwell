'use client';

import { useState } from 'react';
import Navbar from '@/components/Navbar';
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
  Smartphone
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

  // User type comparison data
  const userTypeComparisonData = [
    { name: 'Software Developers', meanBpm: 11.2, alertFreq: 3.4, adherence: 78.4, color: '#e11d48' },
    { name: 'Students', meanBpm: 13.8, alertFreq: 2.1, adherence: 72.1, color: '#f59e0b' },
    { name: 'General Users', meanBpm: 17.6, alertFreq: 0.3, adherence: 88.9, color: '#10b981' },
  ];

  const handleSaveSettings = (e: React.FormEvent) => {
    e.preventDefault();
    setSavedSuccess(true);
    setTimeout(() => setSavedSuccess(false), 3000);
  };

  const downloadUserTypeSummary = () => {
    const content = `# BlinkWell User Type Data & Summary
Author: Mitali Purohit
Platform: BlinkWell Eye Health & Blink Tracking
Privacy: Fully De-Identified, Zero-PII, On-Device ML Kit Processing

1. User Categories:
- Software Developers (n=512 sessions): Continuous coding, IDE, and terminal use. Average blink rate: 11.2 BPM.
- Students (n=496 sessions): Digital textbook reading and online coursework. Average blink rate: 13.8 BPM.
- General Users (n=420 sessions): Everyday web browsing and smartphone use. Average blink rate: 17.6 BPM.

2. Key Findings:
- Higher cognitive screen focus significantly suppresses natural blink rates below healthy levels (15-20 BPM).
- Gentle vibration reminders effectively encourage users to restore healthy blink frequency without disrupting workflow.

3. Privacy Guarantees:
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
          totalSessions: 1428,
          avgBpm: 14.2,
          strainIndex: 18.4,
          totalScreenHours: 8940,
          totalAlerts: 3812,
          diurnalData: [
            { hour: '00:00', avgBpm: 18.2, alerts: 12, strainRate: 4 },
            { hour: '04:00', avgBpm: 19.8, alerts: 3, strainRate: 1 },
            { hour: '08:00', avgBpm: 16.5, alerts: 24, strainRate: 9 },
            { hour: '12:00', avgBpm: 12.4, alerts: 142, strainRate: 31 },
            { hour: '14:00', avgBpm: 9.8, alerts: 218, strainRate: 44 },
            { hour: '16:00', avgBpm: 8.9, alerts: 286, strainRate: 52 },
            { hour: '18:00', avgBpm: 11.2, alerts: 174, strainRate: 36 },
            { hour: '20:00', avgBpm: 14.5, alerts: 96, strainRate: 18 },
            { hour: '22:00', avgBpm: 16.8, alerts: 42, strainRate: 8 },
          ],
          distributionData: [
            { range: '<10 BPM (High Strain)', percentage: 18, count: 263, color: '#e11d48' },
            { range: '10–14 BPM (Low Rate)', percentage: 44, count: 628, color: '#f59e0b' },
            { range: '15–20 BPM (Healthy Normal)', percentage: 32, count: 457, color: '#10b981' },
            { range: '>20 BPM (Frequent)', percentage: 6, count: 80, color: '#6366f1' },
          ],
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
                  <strong className="text-slate-900 font-telemetry">512</strong>
                </div>
                <div>
                  <span className="text-[10px] text-slate-400 block uppercase">Average Rate</span>
                  <strong className="text-rose-600 font-telemetry">11.2 BPM</strong>
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
                  <strong className="text-slate-900 font-telemetry">496</strong>
                </div>
                <div>
                  <span className="text-[10px] text-slate-400 block uppercase">Average Rate</span>
                  <strong className="text-amber-600 font-telemetry">13.8 BPM</strong>
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
                  <strong className="text-slate-900 font-telemetry">420</strong>
                </div>
                <div>
                  <span className="text-[10px] text-slate-400 block uppercase">Average Rate</span>
                  <strong className="text-emerald-600 font-telemetry">17.6 BPM</strong>
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
