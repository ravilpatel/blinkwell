'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import Navbar from '@/components/Navbar';
import { supabase } from '@/lib/supabaseClient';
import { UserPlus, Shield, Trash2, Mail, Lock, CheckCircle, AlertCircle } from 'lucide-react';
import { format } from 'date-fns';

export default function TeamManagementPage() {
  const router = useRouter();
  const [researchers, setResearchers] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [isAdmin, setIsAdmin] = useState(false);

  // New user form state
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState<'researcher' | 'admin'>('researcher');
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

      if (!userIsAdmin) {
        router.push('/overview/');
        return;
      }

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

      if (data) {
        setResearchers(data);
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
      // 1. Sign up the new user
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
        // 2. Add to researchers table
        const { error: insertError } = await supabase.from('researchers').upsert({
          id: data.user.id,
          email: email,
          role: role
        });

        if (insertError) {
          setMessage({ text: insertError.message, type: 'error' });
        } else {
          setMessage({
            text: `Successfully added ${email} as ${role}! They can now log in to the dashboard.`,
            type: 'success'
          });
          setEmail('');
          setPassword('');
          await fetchResearchers();
        }
      }
    } catch (err: any) {
      setMessage({ text: err?.message || 'Failed to add user.', type: 'error' });
    } finally {
      setActionLoading(false);
    }
  };

  const handleDeleteUser = async (id: string, userEmail: string) => {
    if (userEmail === 'viraravil2101@gmail.com') {
      alert('Primary admin account cannot be deleted.');
      return;
    }

    if (!confirm(`Are you sure you want to revoke dashboard access for ${userEmail}?`)) {
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

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-50">
        <Navbar />
        <div className="flex items-center justify-center h-96">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-teal-600"></div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <Navbar />

      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="mb-8">
          <h1 className="text-2xl font-bold text-slate-900">Researcher Team &amp; Access Control</h1>
          <p className="text-sm text-slate-500">
            Manage authorized researchers and administrators who have access to the BlinkWell analytics dashboard.
          </p>
        </div>

        {message && (
          <div
            className={`p-4 mb-6 rounded-xl border flex items-center space-x-3 text-sm ${
              message.type === 'success'
                ? 'bg-emerald-50 border-emerald-200 text-emerald-800'
                : 'bg-rose-50 border-rose-200 text-rose-800'
            }`}
          >
            {message.type === 'success' ? (
              <CheckCircle className="w-5 h-5 flex-shrink-0 text-emerald-600" />
            ) : (
              <AlertCircle className="w-5 h-5 flex-shrink-0 text-rose-600" />
            )}
            <span>{message.text}</span>
          </div>
        )}

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          {/* Add New Researcher Form */}
          <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm h-fit">
            <div className="flex items-center space-x-3 mb-4">
              <div className="p-2 bg-teal-500/10 rounded-xl">
                <UserPlus className="w-5 h-5 text-teal-600" />
              </div>
              <h2 className="text-base font-bold text-slate-900">Add New Researcher</h2>
            </div>

            <form onSubmit={handleAddUser} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                  Email Address
                </label>
                <div className="relative">
                  <Mail className="w-4 h-4 text-slate-400 absolute left-3 top-3" />
                  <input
                    type="email"
                    required
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="colleague@university.edu"
                    className="w-full pl-9 pr-3 py-2 border border-slate-300 rounded-xl text-sm focus:ring-2 focus:ring-teal-500 focus:outline-none"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                  Initial Password
                </label>
                <div className="relative">
                  <Lock className="w-4 h-4 text-slate-400 absolute left-3 top-3" />
                  <input
                    type="password"
                    required
                    minLength={6}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="Min 6 characters"
                    className="w-full pl-9 pr-3 py-2 border border-slate-300 rounded-xl text-sm focus:ring-2 focus:ring-teal-500 focus:outline-none"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                  Role
                </label>
                <select
                  value={role}
                  onChange={(e) => setRole(e.target.value as any)}
                  className="w-full px-3 py-2 bg-white border border-slate-300 rounded-xl text-sm focus:ring-2 focus:ring-teal-500 focus:outline-none"
                >
                  <option value="researcher">Researcher (View &amp; Export Data)</option>
                  <option value="admin">Administrator (Full Access &amp; User Management)</option>
                </select>
              </div>

              <button
                type="submit"
                disabled={actionLoading}
                className="w-full py-2.5 px-4 bg-teal-600 hover:bg-teal-700 text-white font-semibold text-sm rounded-xl shadow-sm transition-colors disabled:opacity-50 mt-2"
              >
                {actionLoading ? 'Creating User...' : 'Grant Dashboard Access'}
              </button>
            </form>
          </div>

          {/* Existing Researchers Table */}
          <div className="lg:col-span-2 bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
            <div className="p-6 border-b border-slate-200">
              <h2 className="text-base font-bold text-slate-900">Authorized Personnel ({researchers.length})</h2>
              <p className="text-xs text-slate-500">Users permitted to sign in to the researcher portal</p>
            </div>

            <div className="divide-y divide-slate-200">
              {researchers.map((user) => (
                <div key={user.id} className="p-4 sm:px-6 flex items-center justify-between hover:bg-slate-50/60 transition-colors">
                  <div className="flex items-center space-x-3">
                    <div className="p-2 bg-slate-100 rounded-full">
                      <Shield className="w-4 h-4 text-slate-600" />
                    </div>
                    <div>
                      <span className="text-sm font-semibold text-slate-900 block">{user.email}</span>
                      <span className="text-xs text-slate-400">
                        Added {user.created_at ? format(new Date(user.created_at), 'MMM d, yyyy') : 'N/A'}
                      </span>
                    </div>
                  </div>

                  <div className="flex items-center space-x-3">
                    <span
                      className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold ${
                        user.role === 'admin'
                          ? 'bg-purple-100 text-purple-800'
                          : 'bg-teal-100 text-teal-800'
                      }`}
                    >
                      {user.role === 'admin' ? 'Admin' : 'Researcher'}
                    </span>

                    {user.email !== 'viraravil2101@gmail.com' && (
                      <button
                        onClick={() => handleDeleteUser(user.id, user.email)}
                        className="p-1.5 text-slate-400 hover:text-rose-600 rounded-lg hover:bg-rose-50 transition-colors"
                        title="Revoke access"
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
