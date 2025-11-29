
import React, { useState, useEffect } from 'react';
import { Home, PieChart, Lock, Book, Settings as SettingsIcon, List, Plus } from 'lucide-react';
import LockScreen from './components/LockScreen';
import Dashboard from './components/Dashboard';
import History from './components/History';
import Logger from './components/Logger';
import Analytics from './components/Analytics';
import Settings from './components/Settings';
import Breathing from './components/Breathing';
import SageJournal from './components/SageJournal';
import { AppData, AppView, LogEntry, UserSettings } from './types';
import { getAppData, saveAppData, addLogEntry } from './services/storageService';

function App() {
  const [view, setView] = useState<AppView>(AppView.LOCKED);
  const [isInitialized, setIsInitialized] = useState(false);
  const [data, setData] = useState<AppData>({ 
    logs: [], 
    settings: { 
      pin: null, 
      username: 'User', 
      language: 'cn', 
      theme: 'dark', 
      biometricsEnabled: false, 
      appIcon: 'calculator' 
    } 
  });
  
  // Initialize Data
  useEffect(() => {
    const loaded = getAppData();
    setData(loaded);
    
    // Logic: If PIN exists, go LOCKED. If not, go DASHBOARD.
    if (loaded.settings.pin) {
        setView(AppView.LOCKED);
    } else {
        setView(AppView.DASHBOARD);
    }
    setIsInitialized(true);
  }, []);

  // Theme Effect
  useEffect(() => {
    if (data.settings.theme === 'dark') {
        document.documentElement.classList.add('dark');
    } else {
        document.documentElement.classList.remove('dark');
    }
  }, [data.settings.theme]);

  // Persist Data on Change
  useEffect(() => {
    if (isInitialized) {
        saveAppData(data);
    }
  }, [data, isInitialized]);

  // Handle Visibility Change (Privacy feature: Lock on minimize)
  useEffect(() => {
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'hidden') {
        if (data.settings.pin) {
            setView(AppView.LOCKED);
        }
      }
    };
    document.addEventListener("visibilitychange", handleVisibilityChange);
    return () => document.removeEventListener("visibilitychange", handleVisibilityChange);
  }, [data.settings.pin]);


  const handleUnlock = () => {
    setView(AppView.DASHBOARD);
  };

  const handleSetPin = (pin: string) => {
    setData(prev => ({ ...prev, settings: { ...prev.settings, pin } }));
  };

  const handleUpdateSettings = (newSettings: UserSettings) => {
     setData(prev => ({ ...prev, settings: newSettings }));
  };

  const handleSaveLog = (entry: LogEntry) => {
    const newLogs = [entry, ...data.logs];
    setData(prev => ({ ...prev, logs: newLogs }));
    addLogEntry(entry);
    setView(AppView.DASHBOARD);
  };

  const handleDeleteLog = (id: string) => {
     const newLogs = data.logs.filter(l => l.id !== id);
     setData(prev => ({ ...prev, logs: newLogs }));
  };

  const handleTogglePinLog = (id: string) => {
     const newLogs = data.logs.map(l => l.id === id ? { ...l, pinned: !l.pinned } : l);
     setData(prev => ({ ...prev, logs: newLogs }));
  };

  const handleSaveNote = (note: string) => {
    const now = Date.now();
    const latestLog = data.logs[0];
    const isRecent = latestLog && (now - latestLog.timestamp < 3600 * 1000);

    if (isRecent) {
        const updatedLogs = [...data.logs];
        updatedLogs[0] = { 
            ...latestLog, 
            journal: latestLog.journal ? `${latestLog.journal}\n\n[Update]: ${note}` : note 
        };
        setData(prev => ({ ...prev, logs: updatedLogs }));
        saveAppData({ ...data, logs: updatedLogs });
    } else {
        const entry: LogEntry = {
            id: crypto.randomUUID(),
            timestamp: now,
            durationSeconds: 0,
            type: 'solo',
            stimuli: [],
            moodPre: 'Neutral',
            moodPost: 'Relaxed',
            pleasureRating: 0,
            journal: note
        };
        handleSaveLog(entry);
    }
    setView(AppView.JOURNAL);
  };

  if (!isInitialized) return null; // Or a loading spinner

  const renderContent = () => {
    switch (view) {
      case AppView.LOCKED:
        return <LockScreen settings={data.settings} onUnlock={handleUnlock} onSetPin={handleSetPin} />;
      
      case AppView.DASHBOARD:
        return <Dashboard logs={data.logs} onChangeView={setView} lang={data.settings.language} hasPin={!!data.settings.pin} onSetPinRequest={() => setView(AppView.SETTINGS)} />;
      
      case AppView.HISTORY:
        return <History logs={data.logs} lang={data.settings.language} onDelete={handleDeleteLog} onTogglePin={handleTogglePinLog} />;
      
      case AppView.LOGGER:
        return <Logger onSave={handleSaveLog} onCancel={() => setView(AppView.DASHBOARD)} lang={data.settings.language} />;
      
      case AppView.ANALYTICS:
        return (
             <div className="px-6 pt-6 animate-fade-in pb-24 text-zen-900 dark:text-zen-100">
                 <Analytics logs={data.logs} lang={data.settings.language} />
             </div>
        );
      
      case AppView.SETTINGS:
        return <Settings settings={data.settings} onUpdate={handleUpdateSettings} />;
      
      case AppView.BREATHING:
        return <Breathing onExit={() => setView(AppView.DASHBOARD)} onSave={handleSaveLog} lang={data.settings.language} />;
      
      case AppView.JOURNAL:
        return <SageJournal logs={data.logs} onAddNote={handleSaveNote} onBack={() => setView(AppView.DASHBOARD)} lang={data.settings.language} />;
      
      default:
        return <Dashboard logs={data.logs} onChangeView={setView} lang={data.settings.language} hasPin={!!data.settings.pin} onSetPinRequest={() => setView(AppView.SETTINGS)} />;
    }
  };

  const mainTabs = [AppView.DASHBOARD, AppView.HISTORY, AppView.LOGGER, AppView.ANALYTICS, AppView.SETTINGS];
  const showNav = mainTabs.includes(view);

  return (
    <div className="max-w-md mx-auto min-h-screen bg-zen-50 dark:bg-zen-950 transition-colors duration-300 relative shadow-2xl overflow-hidden font-sans">
      
      {/* View Content */}
      <main className="h-full min-h-screen overflow-y-auto no-scrollbar">
          {renderContent()}
      </main>

      {/* 5-Column Navigation Bar */}
      {showNav && (
        <nav className="absolute bottom-6 left-4 right-4 h-16 bg-white/90 dark:bg-zen-900/90 backdrop-blur-md rounded-2xl border border-zen-200 dark:border-zen-800 flex justify-around items-center shadow-xl z-50 px-1">
          {/* Tab 1: Home */}
          <button 
            onClick={() => setView(AppView.DASHBOARD)}
            className={`p-2 rounded-xl transition-colors ${view === AppView.DASHBOARD ? 'bg-zen-100 dark:bg-zen-800 text-accent-600 dark:text-accent-500' : 'text-zen-400 hover:text-zen-600 dark:hover:text-zen-300'}`}
          >
            <Home size={22} />
          </button>
          
          {/* Tab 2: History */}
          <button 
            onClick={() => setView(AppView.HISTORY)}
            className={`p-2 rounded-xl transition-colors ${view === AppView.HISTORY ? 'bg-zen-100 dark:bg-zen-800 text-accent-600 dark:text-accent-500' : 'text-zen-400 hover:text-zen-600 dark:hover:text-zen-300'}`}
          >
            <List size={22} />
          </button>

          {/* Tab 3: ADD (Prominent) */}
          <button 
             onClick={() => setView(AppView.LOGGER)}
             className="w-12 h-12 bg-accent-600 rounded-full flex items-center justify-center text-white shadow-lg shadow-accent-500/30 hover:scale-105 transition-transform -mt-6 border-4 border-zen-50 dark:border-zen-950"
          >
             <Plus size={24} strokeWidth={3} />
          </button>

          {/* Tab 4: Analytics */}
          <button 
            onClick={() => setView(AppView.ANALYTICS)}
            className={`p-2 rounded-xl transition-colors ${view === AppView.ANALYTICS ? 'bg-zen-100 dark:bg-zen-800 text-accent-600 dark:text-accent-500' : 'text-zen-400 hover:text-zen-600 dark:hover:text-zen-300'}`}
          >
            <PieChart size={22} />
          </button>

          {/* Tab 5: Settings */}
          <button 
            onClick={() => setView(AppView.SETTINGS)}
            className={`p-2 rounded-xl transition-colors ${view === AppView.SETTINGS ? 'bg-zen-100 dark:bg-zen-800 text-accent-600 dark:text-accent-500' : 'text-zen-400 hover:text-zen-600 dark:hover:text-zen-300'}`}
          >
            <SettingsIcon size={22} />
          </button>
        </nav>
      )}
    </div>
  );
}

export default App;
