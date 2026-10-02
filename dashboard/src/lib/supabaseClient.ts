import { createClient, SupabaseClient } from '@supabase/supabase-js';

const STORAGE_KEY_URL = 'blinkwell_custom_supabase_url';
const STORAGE_KEY_ANON = 'blinkwell_custom_supabase_anon_key';

const envUrl = process.env.NEXT_PUBLIC_SUPABASE_URL || '';
const envAnonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY || '';

export function getActiveSupabaseCredentials(): { url: string; anonKey: string; isCustom: boolean } {
  if (typeof window !== 'undefined') {
    const customUrl = localStorage.getItem(STORAGE_KEY_URL);
    const customAnon = localStorage.getItem(STORAGE_KEY_ANON);
    if (customUrl && customAnon && customUrl.trim() && customAnon.trim()) {
      return {
        url: customUrl.trim(),
        anonKey: customAnon.trim(),
        isCustom: true,
      };
    }
  }

  return {
    url: envUrl.trim(),
    anonKey: envAnonKey.trim(),
    isCustom: false,
  };
}

export function isSupabaseConfigured(): boolean {
  const { url, anonKey } = getActiveSupabaseCredentials();
  if (!url || !anonKey) return false;
  if (url.includes('placeholder') || anonKey.includes('placeholder')) return false;
  return url.startsWith('http://') || url.startsWith('https://');
}

let activeClient: SupabaseClient | null = null;
let currentUrl = '';
let currentAnonKey = '';

export function getSupabaseClient(): SupabaseClient {
  const { url, anonKey } = getActiveSupabaseCredentials();
  const resolvedUrl = url || 'https://placeholder.supabase.co';
  const resolvedKey = anonKey || 'placeholder-anon-key';

  if (!activeClient || currentUrl !== resolvedUrl || currentAnonKey !== resolvedKey) {
    currentUrl = resolvedUrl;
    currentAnonKey = resolvedKey;
    activeClient = createClient(resolvedUrl, resolvedKey, {
      auth: {
        persistSession: true,
        autoRefreshToken: true,
      },
    });
  }

  return activeClient;
}

export function setCustomSupabaseCredentials(url: string, anonKey: string): void {
  if (typeof window !== 'undefined') {
    localStorage.setItem(STORAGE_KEY_URL, url.trim());
    localStorage.setItem(STORAGE_KEY_ANON, anonKey.trim());
    activeClient = null; // force re-creation
  }
}

export function clearCustomSupabaseCredentials(): void {
  if (typeof window !== 'undefined') {
    localStorage.removeItem(STORAGE_KEY_URL);
    localStorage.removeItem(STORAGE_KEY_ANON);
    activeClient = null; // force re-creation
  }
}

/**
 * Proxy object for `supabase` so that any imports of `supabase` automatically
 * route calls to the dynamically resolved active client.
 */
export const supabase = new Proxy({} as SupabaseClient, {
  get(_target, prop) {
    const client = getSupabaseClient();
    const value = (client as any)[prop];
    if (typeof value === 'function') {
      return value.bind(client);
    }
    return value;
  },
});
