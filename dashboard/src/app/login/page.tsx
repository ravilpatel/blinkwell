'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { supabase, isSupabaseConfigured } from '@/lib/supabaseClient';
import SupabaseConfigModal from '@/components/SupabaseConfigModal';
import { Eye, Lock, Mail, AlertCircle, ShieldCheck, Database, AlertTriangle, KeyRound } from 'lucide-react';

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
        const userId = signInData.session.user.id;
        const userEmail = signInData.session.user.email;

        if (userEmail === 'viraravil2101@gmail.com') {
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

      <div className="max-w-md w-full space-y-6 bg-white p-8 sm:p-10 rounded-2xl shadow-xs border border-slate-200">
        <div className="text-center">
          <div className="inline-flex p-3 bg-teal-600 rounded-2xl mb-4 text-white shadow-sm">
            <Eye className="w-8 h-8" />
          </div>
          <h2 className="text-2xl font-bold tracking-tight text-slate-900">
            BlinkWell Research Portal
          </h2>
          <div className="flex items-center justify-center space-x-2 mt-1">
            <span className="text-xs text-slate-500 font-medium">
              By Mitali Purohit • IRB Protocol #2024-884-BW
            </span>
          </div>
        </div>

        {/* Warning if Supabase is unconfigured */}
        {!isConfigured && (
          <div className="p-4 rounded-xl bg-amber-50 border border-amber-200 text-amber-900 text-xs space-y-2">
            <div className="flex items-center space-x-2 font-bold">
              <AlertTriangle className="w-4 h-4 text-amber-600 flex-shrink-0" />
              <span>Supabase Connection Not Configured</span>
            </div>
            <p className="text-amber-700 leading-relaxed">
              The portal is currently using placeholder credentials. Click below to enter your Supabase Project URL and Anon Key.
            </p>
            <button
              type="button"
              onClick={() => setShowConfigModal(true)}
              className="inline-flex items-center px-3 py-1.5 bg-amber-600 hover:bg-amber-700 text-white font-bold rounded-lg transition-colors text-xs"
            >
              <Database className="w-3.5 h-3.5 mr-1" />
              Configure Supabase Database
            </button>
          </div>
        )}

        {error && (
          <div className="p-4 rounded-xl bg-rose-50 border border-rose-200 text-rose-800 text-xs flex items-start space-x-2">
            <AlertCircle className="w-4 h-4 text-rose-600 flex-shrink-0 mt-0.5" />
            <div className="flex-1">
              <strong className="block font-bold">Authentication Error</strong>
              <span>{error}</span>
            </div>
          </div>
        )}

        <form onSubmit={handleLogin} className="space-y-4">
          <div>
            <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1">
              Researcher Email
            </label>
            <div className="relative">
              <Mail className="w-4 h-4 text-slate-400 absolute left-3 top-3" />
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="researcher@university.edu"
                className="w-full pl-9 pr-3 py-2.5 border border-slate-200 bg-slate-50 rounded-xl text-xs focus:ring-2 focus:ring-teal-500 focus:outline-none text-slate-900"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1">
              Security Key / Password
            </label>
            <div className="relative">
              <Lock className="w-4 h-4 text-slate-400 absolute left-3 top-3" />
              <input
                type="password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                className="w-full pl-9 pr-3 py-2.5 border border-slate-200 bg-slate-50 rounded-xl text-xs focus:ring-2 focus:ring-teal-500 focus:outline-none text-slate-900"
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full py-2.5 px-4 bg-teal-600 hover:bg-teal-700 text-white font-bold text-xs rounded-xl shadow-xs transition-colors disabled:opacity-50 mt-2"
          >
            {loading ? 'Authenticating Clinical Credentials...' : 'Sign In to Research Portal'}
          </button>
        </form>

        {/* Database Config Shortcut */}
        <div className="pt-4 border-t border-slate-100 flex items-center justify-between text-xs text-slate-500">
          <span className="flex items-center">
            <ShieldCheck className="w-3.5 h-3.5 text-teal-600 mr-1" />
            256-Bit SSL Encrypted
          </span>
          <button
            type="button"
            onClick={() => setShowConfigModal(true)}
            className="text-teal-700 hover:text-teal-900 font-bold underline"
          >
            Database Settings
          </button>
        </div>
      </div>
    </div>
  );
}
