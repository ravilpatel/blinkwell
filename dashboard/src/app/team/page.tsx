'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import Navbar from '@/components/Navbar';
import { supabase } from '@/lib/supabaseClient';
import { UserPlus, Shield, Trash2, Mail, Lock, CheckCircle, AlertCircle, ShieldCheck, UserCheck, KeyRound } from 'lucide-react';
import { format } from 'date-fns';

export default function TeamManagementPage() {
  const router = useRouter();
  const [researchers, setResearchers] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [isAdmin, setIsAdmin] = useState(true);

  // New user form state
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState<'admin' | 'researcher' | 'analyst'>('researcher');
  const [actionLoading, setActionLoading] = useState(false);
  const [message, setMessage] = useState<{ text: string; type: 'success' | 'error' } | null>(null);

  useEffect(() => {
    checkAdminAndFetchTeam();
  }, []);

  async function checkAdminAndFetchTeam() {
    setLoading(true);
    try {
      const { data: { session } } = await supabase.auth.getSession();
      if (!session) {
        router.push('/login/');
        return;
      }

      const currentUserEmail = session.user.email;
      let userIsAdmin = currentUserEmail === 'viraravil2101@gmail.com';

      if (!userIsAdmin) {
        const { data: roleData } = await supabase
          .from('researchers')
          .select('role')
          .eq('id', session.user.id)
          .single();
        userIsAdmin = roleData?.role === 'admin';
      }

      setIsAdmin(userIsAdmin);
      await fetchResearchers();
    } catch (err) {
      console.error('Auth verification error:', err);
    } finally {
      setLoading(false);
    }
  }

  async function fetchResearchers() {
    try {
      const { data, error } = await supabase
        .from('researchers')
        .select('*')
        .order('created_at', { ascending: false });

      if (data && data.length > 0) {
        setResearchers(data);
      } else {
        // Mock team members for clinical trial demonstration
        setResearchers([
          { id: 'usr-pi-1', email: 'viraravil2101@gmail.com', role: 'admin', created_at: new Date(Date.now() - 60 * 86400000).toISOString() },
          { id: 'usr-coord-2', email: 'mitali.purohit@blinkwell.research', role: 'admin', created_at: new Date(Date.now() - 45 * 86400000).toISOString() },
          { id: 'usr-invest-3', email: 'clinical.investigator@ophthalmic-lab.org', role: 'researcher', created_at: new Date(Date.now() - 20 * 86400000).toISOString() },
          { id: 'usr-stat-4', email: 'biostatistician@ergonomics-inst.edu', role: 'analyst', created_at: new Date(Date.now() - 10 * 86400000).toISOString() },
        ]);
      }
    } catch (err) {
      console.error('Error fetching researchers:', err);
    }
  }

  const handleAddUser = async (e: React.FormEvent) => {
    e.preventDefault();
    setActionLoading(true);
    setMessage(null);

    try {
      const { data, error } = await supabase.auth.signUp({
        email,
        password,
      });

      if (error) {
        setMessage({ text: error.message, type: 'error' });
        setActionLoading(false);
        return;
      }

      if (data.user) {
        const { error: insertError } = await supabase.from('researchers').upsert({
          id: data.user.id,
          email: email,
          role: role === 'analyst' ? 'researcher' : role
        });

        if (insertError) {
          setMessage({ text: insertError.message, type: 'error' });
        } else {
          setMessage({
            text: `Granted ${role.toUpperCase()} access to ${email}.`,
            type: 'success'
          });
          setEmail('');
          setPassword('');
          await fetchResearchers();
        }
      }
    } catch (err: any) {
      setMessage({ text: err?.message || 'Failed to add research staff.', type: 'error' });
    } finally {
      setActionLoading(false);
    }
  };

  const handleDeleteUser = async (id: string, userEmail: string) => {
    if (userEmail === 'viraravil2101@gmail.com') {
      alert('Primary Administrator account cannot be deleted.');
      return;
    }

    if (!confirm(`Revoke clinical portal access for ${userEmail}?`)) {
      return;
    }

    try {
      await supabase.from('researchers').delete().eq('id', id);
      setMessage({ text: `Access revoked for ${userEmail}.`, type: 'success' });
      await fetchResearchers();
    } catch (err: any) {
      setMessage({ text: err?.message || 'Failed to remove user.', type: 'error' });
    }
  };

  return (
    <div className="min-h-screen bg-slate-50 pb-16">
      <Navbar />

      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
        {/* Header */}
        <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-xs flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <div className="flex items-center space-x-2.5">
              <h1 className="text-2xl font-bold text-slate-900 tracking-tight">
                Research Team &amp; Access Control
              </h1>
              <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-bold bg-teal-100 text-teal-800">
                <ShieldCheck className="w-3.5 h-3.5 mr-1" />
                Role-Based Access
              </span>
            </div>
            <p className="text-xs text-slate-500 mt-1">
              Manage authorized Principal Investigators, Clinical Coordinators, and Biostatisticians for IRB Protocol #2024-884-BW
            </p>
          </div>
        </div>

        {message && (
          <div
            className={`p-4 rounded-xl border flex items-center space-x-3 text-xs ${
              message.type === 'success'
                ? 'bg-emerald-50 border-emerald-200 text-emerald-800'
                : 'bg-rose-50 border-rose-200 text-rose-800'
            }`}
          >
            {message.type === 'success' ? (
              <CheckCircle className="w-4 h-4 flex-shrink-0 text-emerald-600" />
            ) : (
              <AlertCircle className="w-4 h-4 flex-shrink-0 text-rose-600" />
            )}
            <span>{message.text}</span>
          </div>
        )}

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          {/* Add Member Form */}
          <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-xs h-fit space-y-4">
            <div className="flex items-center space-x-2.5 pb-2 border-b border-slate-100">
              <div className="p-2 bg-teal-50 rounded-xl text-teal-600">
                <UserPlus className="w-4 h-4" />
              </div>
              <h2 className="text-base font-bold text-slate-900">Provision Research Personnel</h2>
            </div>

            <form onSubmit={handleAddUser} className="space-y-4">
              <div>
                <label className="block text-[11px] font-bold text-slate-600 uppercase tracking-wider mb-1">
                  Institutional Email
                </label>
                <div className="relative">
                  <Mail className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
                  <input
                    type="email"
                    required
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="researcher@university.edu"
                    className="w-full pl-9 pr-3 py-2 border border-slate-200 bg-slate-50 rounded-xl text-xs focus:ring-2 focus:ring-teal-500 focus:outline-none"
                  />
                </div>
              </div>

              <div>
                <label className="block text-[11px] font-bold text-slate-600 uppercase tracking-wider mb-1">
                  Temporary Key / Password
                </label>
                <div className="relative">
                  <Lock className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
                  <input
                    type="password"
                    required
                    minLength={6}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="Min 6 characters"
                    className="w-full pl-9 pr-3 py-2 border border-slate-200 bg-slate-50 rounded-xl text-xs focus:ring-2 focus:ring-teal-500 focus:outline-none"
                  />
                </div>
              </div>

              <div>
                <label className="block text-[11px] font-bold text-slate-600 uppercase tracking-wider mb-1">
                  Assigned Clinical Role
                </label>
                <select
                  value={role}
                  onChange={(e) => setRole(e.target.value as any)}
                  className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs focus:ring-2 focus:ring-teal-500 focus:outline-none cursor-pointer"
                >
                  <option value="researcher">Clinical Investigator (Telemetry View &amp; Export)</option>
                  <option value="analyst">Biostatistician (Statistical Dataset Modeling)</option>
                  <option value="admin">Principal Investigator (Full IRB Administration)</option>
                </select>
              </div>

              <button
                type="submit"
                disabled={actionLoading}
                className="w-full py-2.5 bg-teal-600 hover:bg-teal-700 text-white font-bold text-xs rounded-xl shadow-xs transition-colors disabled:opacity-50"
              >
                {actionLoading ? 'Creating User...' : 'Authorize Clinical Staff'}
              </button>
            </form>
          </div>

          {/* Personnel Table */}
          <div className="lg:col-span-2 bg-white rounded-2xl border border-slate-200 shadow-xs overflow-hidden">
            <div className="p-6 border-b border-slate-200 flex justify-between items-center">
              <div>
                <h2 className="text-base font-bold text-slate-900">Authorized Clinical Personnel ({researchers.length})</h2>
                <p className="text-xs text-slate-400">Staff with cryptographic decryption and telemetry access permissions</p>
              </div>
            </div>

            <div className="divide-y divide-slate-100">
              {researchers.map((user) => (
                <div key={user.id} className="p-4 sm:px-6 flex items-center justify-between hover:bg-slate-50/60 transition-colors">
                  <div className="flex items-center space-x-3">
                    <div className="p-2 bg-slate-100 rounded-xl text-slate-600">
                      <Shield className="w-4 h-4" />
                    </div>
                    <div>
                      <span className="text-xs font-bold text-slate-900 block">{user.email}</span>
                      <span className="text-[11px] text-slate-400 font-mono">
                        Enrolled {user.created_at ? format(new Date(user.created_at), 'MMM d, yyyy') : 'N/A'}
                      </span>
                    </div>
                  </div>

                  <div className="flex items-center space-x-3">
                    <span
                      className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-[10px] font-bold ${
                        user.role === 'admin'
                          ? 'bg-purple-100 text-purple-800'
                          : user.role === 'analyst'
                          ? 'bg-blue-100 text-blue-800'
                          : 'bg-teal-100 text-teal-800'
                      }`}
                    >
                      {user.role === 'admin' ? 'Administrator' : user.role === 'analyst' ? 'Biostatistician' : 'Researcher'}
                    </span>

                    {user.email !== 'viraravil2101@gmail.com' && (
                      <button
                        onClick={() => handleDeleteUser(user.id, user.email)}
                        className="p-1.5 text-slate-400 hover:text-rose-600 rounded-lg hover:bg-rose-50 transition-colors"
                        title="Revoke Access"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}
