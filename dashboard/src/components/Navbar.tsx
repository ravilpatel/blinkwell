'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { supabase } from '@/lib/supabaseClient';
import { 
  Eye, 
  Activity, 
  FileText, 
  UserCheck, 
  LogOut
} from 'lucide-react';

export default function Navbar() {
  const pathname = usePathname();
  const router = useRouter();
  const [userEmail, setUserEmail] = useState<string | null>(null);

  useEffect(() => {
    async function getUserDetails() {
      try {
        const { data: { session } } = await supabase.auth.getSession();
        if (session?.user) {
          setUserEmail(session.user.email || null);
        }
      } catch (err) {
        console.error('Navbar session check error:', err);
      }
    }
    getUserDetails();
  }, []);

  const handleSignOut = async () => {
    try {
      await supabase.auth.signOut();
    } catch (ignored) {}
    router.push('/login/');
  };

  const navLinks = [
    { name: 'Overview', href: '/overview/', icon: Activity },
    { name: 'User Types', href: '/protocols/', icon: FileText },
    { name: 'Team & Permissions', href: '/team/', icon: UserCheck },
  ];

  return (
    <header className="bg-white border-b border-slate-200 sticky top-0 z-50 shadow-xs">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between items-center h-16">
          {/* Brand Logo & Subtitle */}
          <div className="flex items-center space-x-6">
            <Link href="/overview/" className="flex items-center space-x-3 group">
              <div className="p-2 bg-teal-600 rounded-xl text-white shadow-sm group-hover:bg-teal-700 transition-colors">
                <Eye className="w-5 h-5" />
              </div>
              <div>
                <div className="flex items-center space-x-2">
                  <span className="font-bold text-base text-slate-900 tracking-tight">BlinkWell</span>
                </div>
                <span className="text-[11px] text-slate-500 font-medium hidden sm:block">
                  Research Portal • By Mitali Purohit
                </span>
              </div>
            </Link>

            {/* Navigation Bar */}
            <nav className="hidden md:flex space-x-1">
              {navLinks.map((link) => {
                const Icon = link.icon;
                const isActive = 
                  pathname === link.href || 
                  (link.href !== '/overview/' && pathname?.startsWith(link.href));
                return (
                  <Link
                    key={link.name}
                    href={link.href}
                    className={`inline-flex items-center px-3 py-2 text-xs font-semibold rounded-lg transition-colors ${
                      isActive
                        ? 'bg-teal-50 text-teal-800 border border-teal-200/80 shadow-xs'
                        : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100'
                    }`}
                  >
                    <Icon className="w-3.5 h-3.5 mr-1.5 text-teal-600" />
                    {link.name}
                  </Link>
                );
              })}
            </nav>
          </div>

          {/* Right Profile & Controls */}
          <div className="flex items-center space-x-3">
            {/* User Email ID */}
            {userEmail && (
              <div className="text-right hidden sm:block border-l border-slate-200 pl-3">
                <span className="text-xs font-semibold text-slate-800 block truncate max-w-[200px]" title={userEmail}>
                  {userEmail}
                </span>
              </div>
            )}

            {/* Sign Out Button */}
            <button
              onClick={handleSignOut}
              className="inline-flex items-center px-2.5 py-1.5 text-xs font-semibold text-slate-600 hover:text-rose-600 rounded-lg hover:bg-rose-50 border border-slate-200 transition-colors"
              title="Sign Out"
            >
              <LogOut className="w-3.5 h-3.5 sm:mr-1.5" />
              <span className="hidden sm:inline">Sign Out</span>
            </button>
          </div>
        </div>
      </div>

      {/* Mobile Navigation bar */}
      <div className="md:hidden flex overflow-x-auto border-t border-slate-200 px-4 py-2 space-x-2 bg-slate-50">
        {navLinks.map((link) => {
          const Icon = link.icon;
          const isActive = pathname === link.href || (link.href !== '/overview/' && pathname?.startsWith(link.href));
          return (
            <Link
              key={link.name}
              href={link.href}
              className={`inline-flex items-center px-2.5 py-1.5 text-xs font-semibold rounded-lg whitespace-nowrap ${
                isActive
                  ? 'bg-teal-600 text-white'
                  : 'text-slate-600 hover:bg-slate-200'
              }`}
            >
              <Icon className="w-3 h-3 mr-1" />
              {link.name}
            </Link>
          );
        })}
      </div>
    </header>
  );
}
