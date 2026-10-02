'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { supabase } from '@/lib/supabaseClient';
import { Eye, BarChart3, Users, UserPlus, LogOut, Radio } from 'lucide-react';

export default function Navbar() {
  const pathname = usePathname();
  const router = useRouter();
  const [userEmail, setUserEmail] = useState<string | null>(null);
  const [isAdmin, setIsAdmin] = useState(false);

  useEffect(() => {
    async function getUserDetails() {
      const { data: { session } } = await supabase.auth.getSession();
      if (session?.user) {
        setUserEmail(session.user.email || null);
        
        // Check if user is admin
        if (session.user.email === 'viraravil2101@gmail.com') {
          setIsAdmin(true);
        } else {
          const { data } = await supabase
            .from('researchers')
            .select('role')
            .eq('id', session.user.id)
            .single();
          if (data?.role === 'admin') {
            setIsAdmin(true);
          }
        }
      }
    }
    getUserDetails();
  }, []);

  const handleSignOut = async () => {
    await supabase.auth.signOut();
    router.push('/login');
  };

  const navLinks = [
    { name: 'Overview', href: '/overview/', icon: BarChart3 },
    { name: 'Consented Users', href: '/users/', icon: Users },
  ];

  if (isAdmin) {
    navLinks.push({ name: 'Team & Admins', href: '/team/', icon: UserPlus });
  }

  return (
    <header className="bg-white border-b border-slate-200 sticky top-0 z-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between h-16">
          <div className="flex items-center space-x-6">
            <Link href="/overview/" className="flex items-center space-x-3">
              <div className="p-2 bg-teal-500/10 rounded-xl">
                <Eye className="w-6 h-6 text-teal-600" />
              </div>
              <div>
                <span className="font-bold text-lg text-slate-900">BlinkWell</span>
                <span className="text-xs text-slate-500 hidden sm:block">Research Portal • By Mitali Purohit</span>
              </div>
            </Link>

            <nav className="flex space-x-2">
              {navLinks.map((link) => {
                const Icon = link.icon;
                const isActive = pathname === link.href || (link.href !== '/overview/' && pathname?.startsWith(link.href));
                return (
                  <Link
                    key={link.name}
                    href={link.href}
                    className={`inline-flex items-center px-3 py-2 text-sm font-medium rounded-xl transition-colors ${
                      isActive
                        ? 'bg-teal-50 text-teal-700 font-semibold'
                        : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100'
                    }`}
                  >
                    <Icon className="w-4 h-4 mr-1.5" />
                    {link.name}
                  </Link>
                );
              })}
            </nav>
          </div>

          <div className="flex items-center space-x-3">
            {/* Realtime Live Pulse Indicator */}
            <div className="hidden sm:inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
              <span className="w-2 h-2 mr-1.5 bg-emerald-500 rounded-full animate-pulse"></span>
              Realtime Active
            </div>

            {userEmail && (
              <div className="text-right hidden md:block">
                <span className="text-xs font-semibold text-slate-800 block truncate max-w-[160px]">
                  {userEmail}
                </span>
                <span className="text-[10px] text-teal-600 font-bold uppercase tracking-wider">
                  {isAdmin ? 'Admin' : 'Researcher'}
                </span>
              </div>
            )}

            <button
              onClick={handleSignOut}
              className="inline-flex items-center px-3 py-1.5 text-xs font-semibold text-slate-600 hover:text-rose-600 rounded-xl hover:bg-rose-50 border border-slate-200 transition-colors"
            >
              <LogOut className="w-3.5 h-3.5 mr-1.5" />
              Sign Out
            </button>
          </div>
        </div>
      </div>
    </header>
  );
}
