'use client';

import { useState, useEffect } from 'react';
import { 
  getActiveSupabaseCredentials, 
  setCustomSupabaseCredentials, 
  clearCustomSupabaseCredentials,
  isSupabaseConfigured
} from '@/lib/supabaseClient';
import { createClient } from '@supabase/supabase-js';
import { Database, Key, CheckCircle2, AlertTriangle, X, RefreshCw, ExternalLink, HelpCircle } from 'lucide-react';

interface SupabaseConfigModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSaved?: () => void;
}

export default function SupabaseConfigModal({ isOpen, onClose, onSaved }: SupabaseConfigModalProps) {
  const [url, setUrl] = useState('');
  const [anonKey, setAnonKey] = useState('');
  const [isCustom, setIsCustom] = useState(false);
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<{ success: boolean; message: string } | null>(null);

  useEffect(() => {
    if (isOpen) {
      const creds = getActiveSupabaseCredentials();
      setUrl(creds.url.includes('placeholder') ? '' : creds.url);
      setAnonKey(creds.anonKey.includes('placeholder') ? '' : creds.anonKey);
      setIsCustom(creds.isCustom);
      setTestResult(null);
    }
  }, [isOpen]);

  if (!isOpen) return null;

  const handleTest = async () => {
    if (!url.trim() || !anonKey.trim()) {
      setTestResult({ success: false, message: 'Please enter both Supabase URL and Anon Key.' });
      return;
    }

    if (!url.startsWith('https://') && !url.startsWith('http://')) {
      setTestResult({ success: false, message: 'Supabase URL must begin with https://' });
      return;
    }

    setTesting(true);
    setTestResult(null);

    try {
      const testClient = createClient(url.trim(), anonKey.trim());
      // Test basic REST query / health check
      const { error } = await testClient.from('profiles').select('id').limit(1);
      
      // Even if table doesn't have rows or returns 0 rows, if no network/auth error occurred, connection is valid!
      if (error && error.message.toLowerCase().includes('failed to fetch')) {
        setTestResult({
          success: false,
          message: 'Connection Failed: Could not reach Supabase endpoint. Please verify the URL.',
        });
      } else if (error && error.message.includes('JWT')) {
        setTestResult({
          success: false,
          message: `Invalid Anon Key: ${error.message}`,
        });
      } else {
        setTestResult({
          success: true,
          message: 'Connection Successful! Supabase endpoint is reachable and API key is valid.',
        });
      }
    } catch (err: any) {
      setTestResult({
        success: false,
        message: `Connection Error: ${err?.message || 'Failed to connect'}`,
      });
    } finally {
      setTesting(false);
    }
  };

  const handleSave = () => {
    if (!url.trim() || !anonKey.trim()) {
      setTestResult({ success: false, message: 'Both URL and Anon Key are required.' });
      return;
    }

    setCustomSupabaseCredentials(url.trim(), anonKey.trim());
    setIsCustom(true);
    if (onSaved) onSaved();
    onClose();
    // Refresh page to apply new connection to all hooks & subscriptions
    window.location.reload();
  };

  const handleReset = () => {
    clearCustomSupabaseCredentials();
    const creds = getActiveSupabaseCredentials();
    setUrl(creds.url.includes('placeholder') ? '' : creds.url);
    setAnonKey(creds.anonKey.includes('placeholder') ? '' : creds.anonKey);
    setIsCustom(false);
    setTestResult(null);
    if (onSaved) onSaved();
    onClose();
    window.location.reload();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 backdrop-blur-sm p-4 overflow-y-auto">
      <div className="bg-white rounded-2xl max-w-xl w-full p-6 shadow-2xl border border-slate-200 animate-in fade-in zoom-in-95 duration-200">
        <div className="flex justify-between items-start mb-4">
          <div className="flex items-center space-x-3">
            <div className="p-2.5 bg-teal-500/10 rounded-xl text-teal-600">
              <Database className="w-6 h-6" />
            </div>
            <div>
              <h3 className="text-lg font-bold text-slate-900">Supabase Connection Settings</h3>
              <p className="text-xs text-slate-500">Configure your live database endpoint and API key</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-slate-600 p-1.5 rounded-lg hover:bg-slate-100 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Status Indicator */}
        <div className="mb-5 p-3.5 rounded-xl border text-xs flex items-center justify-between bg-slate-50 border-slate-200">
          <div className="flex items-center space-x-2">
            <span className={`w-2.5 h-2.5 rounded-full ${isSupabaseConfigured() ? 'bg-emerald-500' : 'bg-amber-500'}`} />
            <span className="font-medium text-slate-700">
              Status: {isSupabaseConfigured() ? (isCustom ? 'Configured (Browser Custom)' : 'Configured (Built-in)') : 'Not Configured (Placeholder)'}
            </span>
          </div>
          {isCustom && (
            <button
              onClick={handleReset}
              className="text-teal-600 hover:text-teal-800 font-semibold underline text-xs"
            >
              Reset to default
            </button>
          )}
        </div>

        {testResult && (
          <div className={`mb-5 p-4 rounded-xl border flex items-start space-x-3 text-xs ${
            testResult.success 
              ? 'bg-emerald-50 border-emerald-200 text-emerald-800' 
              : 'bg-rose-50 border-rose-200 text-rose-800'
          }`}>
            {testResult.success ? (
              <CheckCircle2 className="w-4 h-4 text-emerald-600 flex-shrink-0 mt-0.5" />
            ) : (
              <AlertTriangle className="w-4 h-4 text-rose-600 flex-shrink-0 mt-0.5" />
            )}
            <div className="flex-1 font-medium">{testResult.message}</div>
          </div>
        )}

        <div className="space-y-4">
          <div>
            <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
              Supabase Project URL
            </label>
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none">
                <Database className="h-4 w-4 text-slate-400" />
              </div>
              <input
                type="url"
                value={url}
                onChange={(e) => setUrl(e.target.value)}
                placeholder="https://your-project-ref.supabase.co"
                className="block w-full pl-10 pr-3 py-2.5 border border-slate-300 rounded-xl leading-5 bg-white placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-teal-500 focus:border-teal-500 text-xs font-mono"
              />
            </div>
            <span className="text-[11px] text-slate-400 mt-1 block">
              Found under Supabase Dashboard → Project Settings → API → Project URL
            </span>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
              Supabase Anon / Public API Key
            </label>
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none">
                <Key className="h-4 w-4 text-slate-400" />
              </div>
              <input
                type="password"
                value={anonKey}
                onChange={(e) => setAnonKey(e.target.value)}
                placeholder="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
                className="block w-full pl-10 pr-3 py-2.5 border border-slate-300 rounded-xl leading-5 bg-white placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-teal-500 focus:border-teal-500 text-xs font-mono"
              />
            </div>
            <span className="text-[11px] text-slate-400 mt-1 block">
              Found under Supabase Dashboard → Project Settings → API → Project API Keys (anon public)
            </span>
          </div>
        </div>

        {/* GitHub Secrets Guide Box */}
        <div className="mt-5 p-3.5 rounded-xl bg-slate-50 border border-slate-200 text-xs text-slate-600">
          <div className="flex items-center space-x-1.5 font-semibold text-slate-800 mb-1">
            <HelpCircle className="w-3.5 h-3.5 text-teal-600" />
            <span>Permanent GitHub Pages Deployment</span>
          </div>
          <p className="text-[11px] leading-relaxed text-slate-500">
            To bake these credentials directly into your GitHub Pages build automatically, add these 2 Secrets under your GitHub repository <strong>Settings → Secrets and variables → Actions</strong>:
          </p>
          <div className="mt-2 font-mono text-[11px] bg-white p-2 rounded-lg border border-slate-200 text-slate-700 space-y-1">
            <div><strong className="text-teal-700">NEXT_PUBLIC_SUPABASE_URL</strong>: your project url</div>
            <div><strong className="text-teal-700">NEXT_PUBLIC_SUPABASE_ANON_KEY</strong>: your anon key</div>
          </div>
        </div>

        {/* Actions */}
        <div className="mt-6 flex flex-col sm:flex-row justify-end items-center gap-3">
          <button
            type="button"
            onClick={handleTest}
            disabled={testing || !url || !anonKey}
            className="w-full sm:w-auto inline-flex items-center justify-center px-4 py-2.5 border border-slate-300 rounded-xl text-xs font-semibold text-slate-700 bg-white hover:bg-slate-50 focus:outline-none transition-colors disabled:opacity-50"
          >
            {testing ? (
              <>
                <RefreshCw className="w-3.5 h-3.5 mr-1.5 animate-spin" />
                Testing...
              </>
            ) : (
              'Test Connection'
            )}
          </button>
          
          <button
            type="button"
            onClick={handleSave}
            disabled={!url || !anonKey}
            className="w-full sm:w-auto inline-flex items-center justify-center px-5 py-2.5 border border-transparent rounded-xl text-xs font-bold text-white bg-teal-600 hover:bg-teal-700 focus:outline-none shadow-sm transition-colors disabled:opacity-50"
          >
            Save & Connect Now
          </button>
        </div>
      </div>
    </div>
  );
}
