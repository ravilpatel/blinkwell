'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { supabase, isSupabaseConfigured } from '@/lib/supabaseClient';
import SupabaseConfigModal from '@/components/SupabaseConfigModal';
import { Eye, Lock, Mail, AlertCircle, ShieldCheck, Settings2, Database, AlertTriangle } from 'lucide-react';

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState('viraravil2101@gmail.com');
  const [password, setPassword] = useState('123456789');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [showConfigModal, setShowConfigModal] = useState(false);
  const [isConfigured, setIsConfigured] = useState(true);

  useEffect(() => {
    setIsConfigured(isSupabaseConfigured());
  }, []);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!isSupabaseConfigured()) {
      setShowConfigModal(true);
      setError('Supabase connection is not configured. Please set your Supabase Project URL and Anon Key.');
      return;
    }

    setLoading(true);

    try {
      // 1. Try Signing In
      const { data: signInData, error: signInError } = await supabase.auth.signInWithPassword({
        email,
        password,
      });

      if (signInError) {
        const errorMsg = signInError.message.toLowerCase();

        // Failed to fetch check
        if (errorMsg.includes('failed to fetch')) {
          setError('Failed to fetch: Unable to reach your Supabase endpoint. Please verify your Supabase URL & Anon Key.');
          setLoading(false);
          return;
        }

        // If initial admin login fails due to user not existing yet, sign up initial admin
        if (email === 'viraravil2101@gmail.com' && errorMsg.includes('invalid login credentials')) {
          const { data: signUpData, error: signUpError } = await supabase.auth.signUp({
            email,
            password,
          });

          if (signUpError) {
            setError(signUpError.message);
            setLoading(false);
            return;
          }

          if (signUpData.session) {
            // Ensure record in researchers table
            try {
              await supabase.from('researchers').upsert({
                id: signUpData.session.user.id,
                email: email,
                role: 'admin'
              });
            } catch (ignored) {}

            router.push('/overview/');
            return;
          } else {
            setError('Admin account created. Please confirm your email in Supabase (or disable Email Confirmations in Supabase Auth settings) and sign in.');
            setLoading(false);
            return;
          }
        }

        setError(signInError.message);
      } else if (signInData.session) {
        // Verify researcher authorization
        const userId = signInData.session.user.id;
        const userEmail = signInData.session.user.email;

        if (userEmail === 'viraravil2101@gmail.com') {
          // Ensure super-admin record exists
          try {
            await supabase.from('researchers').upsert({
              id: userId,
              email: userEmail,
              role: 'admin'
            });
          } catch (ignored) {}
        }

        router.push('/overview/');
      }
    } catch (err: any) {
      const msg = err?.message || 'An unexpected error occurred during login.';
      if (msg.toLowerCase().includes('failed to fetch')) {
        setError('Failed to fetch: Could not reach Supabase database. Please check your Supabase URL & Anon Key.');
      } else {
        setError(msg);
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50 px-4 py-12 sm:px-6 lg:px-8">
      <SupabaseConfigModal
        isOpen={showConfigModal}
        onClose={() => {
          setShowConfigModal(false);
          setIsConfigured(isSupabaseConfigured());
        }}
      />

      <div className="max-w-md w-full space-y-6 bg-white p-8 sm:p-10 rounded-2xl shadow-sm border border-slate-200">
        <div className="text-center">
          <div className="inline-flex p-3 bg-teal-500/10 rounded-2xl mb-4">
            <Eye className="w-10 h-10 text-teal-600" />
          </div>
          <h2 className="text-2xl font-bold tracking-tight text-slate-900">
            BlinkWell Research Portal
          </h2>
          <p className="mt-1 text-xs text-slate-500">
            By Mitali Purohit • Authorized Personnel Only
          </p>
        </div>

        {/* Warning if Supabase is unconfigured */}
        {!isConfigured && (
          <div className="p-4 rounded-xl bg-amber-50 border border-amber-200 text-amber-900 text-xs space-y-2">
            <div className="flex items-center space-x-2 font-semibold">
              <AlertTriangle className="w-4 h-4 text-amber-600 flex-shrink-0" />
              <span>Supabase Connection Not Configured</span>
            </div>
            <p className="text-amber-700 leading-relaxed">
              The live dashboard is currently using placeholder credentials, which causes &quot;Failed to fetch&quot;.
            </p>
            <button
              type="button"
              onClick={() => setShowConfigModal(true)}
              className="inline-flex items-center px-3 py-1.5 bg-amber-600 hover:bg-amber-700 text-white font-bold rounded-lg transition-colors text-xs"
            >
              <Database className="w-3.5 h-3.5 mr-1.5" />
              Configure Supabase Connection
            </button>
          </div>
        )}

        {/* Primary Admin Notice */}
        <div className="p-3.5 rounded-xl bg-teal-50/70 border border-teal-100 flex items-start space-x-3 text-teal-900 text-xs">
          <ShieldCheck className="w-4 h-4 text-teal-600 flex-shrink-0 mt-0.5" />
          <div>
            <span className="font-semibold block">Primary Admin Account</span>
            <span className="text-teal-700">viraravil2101@gmail.com (Default Admin)</span>
          </div>
        </div>

        {error && (
          <div className="p-4 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-xs space-y-2">
            <div className="flex items-start space-x-2.5">
              <AlertCircle className="w-4 h-4 flex-shrink-0 mt-0.5" />
              <span className="font-medium flex-1">{error}</span>
            </div>
            {error.includes('Failed to fetch') && (
              <button
                type="button"
                onClick={() => setShowConfigModal(true)}
                className="text-xs font-bold text-rose-800 underline hover:text-rose-900 block"
              >
                Click here to review / update Supabase Connection Settings
              </button>
            )}
          </div>
        )}

        <form className="space-y-4" onSubmit={handleLogin}>
          <div>
            <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
              Researcher Email
            </label>
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none">
                <Mail className="h-4 w-4 text-slate-400" />
              </div>
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="researcher@example.com"
                className="block w-full pl-10 pr-3 py-2.5 border border-slate-300 rounded-xl leading-5 bg-white placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-teal-500 focus:border-teal-500 text-sm font-medium"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
              Password
            </label>
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none">
                <Lock className="h-4 w-4 text-slate-400" />
              </div>
              <input
                type="password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                className="block w-full pl-10 pr-3 py-2.5 border border-slate-300 rounded-xl leading-5 bg-white placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-teal-500 focus:border-teal-500 text-sm font-medium"
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full flex justify-center py-3 px-4 border border-transparent rounded-xl shadow-sm text-sm font-bold text-white bg-teal-600 hover:bg-teal-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-teal-500 transition-all disabled:opacity-50"
          >
            {loading ? 'Authenticating...' : 'Sign In to Dashboard'}
          </button>
        </form>

        <div className="pt-3 border-t border-slate-100 flex items-center justify-between text-xs text-slate-500">
          <span>Connection Settings:</span>
          <button
            type="button"
            onClick={() => setShowConfigModal(true)}
            className="inline-flex items-center text-teal-600 hover:text-teal-800 font-semibold transition-colors"
          >
            <Settings2 className="w-3.5 h-3.5 mr-1" />
            Configure Supabase
          </button>
        </div>
      </div>
    </div>
  );
}
