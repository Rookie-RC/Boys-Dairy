
import React, { useState } from 'react';
import { ChevronLeft, ChevronRight, Wind, Brain, Flame, Info, Joystick, Swords, AlertTriangle, ShieldCheck, Activity, Lock } from 'lucide-react';
import { LogEntry, AppView, Language } from '../types';

interface DashboardProps {
  logs: LogEntry[];
  onChangeView: (view: AppView) => void;
  lang: Language;
  hasPin: boolean;
  onSetPinRequest: () => void;
}

const t = {
  en: {
    streak: "Streak",
    days: "days",
    streakExplainer: "Days since last session",
    warning: "Health Status",
    statusGreen: "Healthy Balance",
    statusYellow: "Frequent Activity",
    statusRed: "Overactive Warning",
    statusDescGreen: "Your frequency is within a balanced range.",
    statusDescYellow: "You have been active for 2 consecutive days. Monitor your energy.",
    statusDescRed: "Activity detected for 3+ consecutive days. Consider a break.",
    urge: "Urge Surf",
    sage: "Sage Mode",
    today: "Today",
    calTitle: "Heatmap Calendar",
    solo: "Solo Play",
    partner: "Co-op Mode",
    urgeLabel: "Urge Surfing",
    securityAlert: "App Unsecured",
    setPin: "Set PIN Now"
  },
  cn: {
    streak: "连胜",
    days: "天",
    streakExplainer: "距离上次记录的天数",
    warning: "健康预警",
    statusGreen: "状态良好",
    statusYellow: "频率较高",
    statusRed: "频率过高",
    statusDescGreen: "您的频率处于平衡范围内。",
    statusDescYellow: "连续2天有记录，请注意精力分配。",
    statusDescRed: "连续3天以上有记录，建议适当休息。",
    urge: "冲动抑制",
    sage: "贤者笔记",
    today: "今日",
    calTitle: "热力日历",
    solo: "单人副本",
    partner: "双人成行",
    urgeLabel: "冲动抑制",
    securityAlert: "未设置密码",
    setPin: "立即设置"
  }
};

const Dashboard: React.FC<DashboardProps> = ({ logs, onChangeView, lang, hasPin, onSetPinRequest }) => {
  const [currentDate, setCurrentDate] = useState(new Date());
  const [showStreakInfo, setShowStreakInfo] = useState(false);
  const [activeCard, setActiveCard] = useState<'streak' | 'warning'>('streak');
  const [showPinAlert, setShowPinAlert] = useState(!hasPin);
  
  const txt = t[lang];

  // Helper to get streak (ignore 'urge' type for streak calculation)
  const sessionLogs = logs.filter(l => l.type === 'solo' || l.type === 'partner');
  const getLastLogDate = () => sessionLogs.length > 0 ? new Date(sessionLogs[0].timestamp) : null;
  const lastLog = getLastLogDate();
  const daysSince = lastLog 
    ? Math.floor((Date.now() - lastLog.getTime()) / (1000 * 60 * 60 * 24)) 
    : 0;

  // Warning Logic
  const getConsecutiveDays = () => {
    const uniqueDates = Array.from(new Set(sessionLogs.map(l => new Date(l.timestamp).toDateString()))).map(d => new Date(d).getTime()).sort((a,b) => b - a);
    if (uniqueDates.length === 0) return 0;

    let consecutive = 1;
    // Check if the most recent log is today or yesterday
    const today = new Date().setHours(0,0,0,0);
    const lastLogDay = uniqueDates[0];
    const diffToLast = (today - lastLogDay) / (1000 * 60 * 60 * 24);
    
    // If last log was more than 1 day ago, current consecutive run is broken
    if (diffToLast > 1) return 0;

    for (let i = 0; i < uniqueDates.length - 1; i++) {
        const curr = uniqueDates[i];
        const prev = uniqueDates[i+1];
        const diffDays = (curr - prev) / (1000 * 60 * 60 * 24);
        if (Math.round(diffDays) === 1) {
            consecutive++;
        } else {
            break;
        }
    }
    return consecutive;
  };

  const consecutiveDays = getConsecutiveDays();
  let warningStatus: 'green' | 'yellow' | 'red' = 'green';
  if (consecutiveDays >= 3) warningStatus = 'red';
  else if (consecutiveDays === 2) warningStatus = 'yellow';

  // Calendar Helpers
  const getDaysInMonth = (date: Date) => new Date(date.getFullYear(), date.getMonth() + 1, 0).getDate();
  const getFirstDayOfMonth = (date: Date) => new Date(date.getFullYear(), date.getMonth(), 1).getDay();

  const changeMonth = (offset: number) => {
    setCurrentDate(new Date(currentDate.getFullYear(), currentDate.getMonth() + offset, 1));
  };

  const daysInMonth = getDaysInMonth(currentDate);
  const firstDay = getFirstDayOfMonth(currentDate);
  const monthName = currentDate.toLocaleString(lang === 'cn' ? 'zh-CN' : 'en-US', { month: 'long', year: 'numeric' });

  // Generate Calendar Grid
  const calendarDays = [];
  for (let i = 0; i < firstDay; i++) calendarDays.push(null);
  for (let i = 1; i <= daysInMonth; i++) calendarDays.push(new Date(currentDate.getFullYear(), currentDate.getMonth(), i));

  const getDayLogs = (date: Date) => {
    return logs.filter(l => {
      const d = new Date(l.timestamp);
      return d.getDate() === date.getDate() && 
             d.getMonth() === date.getMonth() && 
             d.getFullYear() === date.getFullYear();
    });
  };

  const handleSwipe = () => {
      setActiveCard(prev => prev === 'streak' ? 'warning' : 'streak');
  }

  return (
    <div className="pb-24 pt-6 px-6 space-y-6 animate-fade-in text-zen-900 dark:text-zen-100">
      
      {/* Security Alert */}
      {!hasPin && showPinAlert && (
          <div className="bg-orange-50 dark:bg-orange-900/20 border border-orange-200 dark:border-orange-800 rounded-xl p-3 flex items-center justify-between">
              <div className="flex items-center gap-3">
                  <Lock size={16} className="text-orange-500" />
                  <span className="text-sm font-medium text-orange-700 dark:text-orange-300">{txt.securityAlert}</span>
              </div>
              <div className="flex gap-2">
                  <button onClick={() => setShowPinAlert(false)} className="text-xs text-zen-400 font-medium px-2 py-1">Dismiss</button>
                  <button onClick={onSetPinRequest} className="text-xs bg-orange-500 text-white px-3 py-1 rounded-lg font-bold">{txt.setPin}</button>
              </div>
          </div>
      )}

      {/* Header Cards (Swipeable) */}
      <div className="relative overflow-hidden" onClick={handleSwipe}>
        <div className={`transition-transform duration-500 ease-in-out ${activeCard === 'warning' ? '-translate-x-full' : 'translate-x-0'} flex`}>
            
            {/* Streak Card */}
            <div className="w-full shrink-0 pr-1">
                <div className="relative flex justify-between items-center bg-zen-100 dark:bg-zen-900 p-6 rounded-2xl shadow-sm border border-zen-200 dark:border-zen-800 h-32">
                    <div>
                        <div className="flex items-center gap-1 text-xs font-bold text-zen-500 uppercase tracking-widest mb-1">
                            {txt.streak} 
                        </div>
                        <div className="flex items-baseline gap-1">
                            <span className="text-4xl font-light">{daysSince}</span>
                            <span className="text-sm text-zen-400">{txt.days}</span>
                        </div>
                    </div>
                    <div className="h-12 w-12 rounded-full bg-accent-500/10 flex items-center justify-center text-accent-500">
                        <Flame size={24} />
                    </div>
                    {/* Dots */}
                    <div className="absolute bottom-3 left-0 right-0 flex justify-center gap-1">
                        <div className="w-1.5 h-1.5 rounded-full bg-zen-400"></div>
                        <div className="w-1.5 h-1.5 rounded-full bg-zen-200 dark:bg-zen-700"></div>
                    </div>
                </div>
            </div>

            {/* Warning Card */}
            <div className="w-full shrink-0 pl-1">
                 <div className={`relative flex justify-between items-center p-6 rounded-2xl shadow-sm border h-32 transition-colors ${
                     warningStatus === 'red' ? 'bg-red-50 dark:bg-red-900/20 border-red-200 dark:border-red-900' :
                     warningStatus === 'yellow' ? 'bg-yellow-50 dark:bg-yellow-900/20 border-yellow-200 dark:border-yellow-900' :
                     'bg-green-50 dark:bg-green-900/20 border-green-200 dark:border-green-900'
                 }`}>
                     <div className="flex-1 mr-4">
                        <div className={`text-xs font-bold uppercase tracking-widest mb-1 ${
                            warningStatus === 'red' ? 'text-red-500' : warningStatus === 'yellow' ? 'text-yellow-600' : 'text-green-600'
                        }`}>
                            {txt.warning}
                        </div>
                        <h3 className={`font-medium mb-1 ${
                             warningStatus === 'red' ? 'text-red-700 dark:text-red-400' : warningStatus === 'yellow' ? 'text-yellow-800 dark:text-yellow-400' : 'text-green-800 dark:text-green-400'
                        }`}>
                            {warningStatus === 'red' ? txt.statusRed : warningStatus === 'yellow' ? txt.statusYellow : txt.statusGreen}
                        </h3>
                     </div>
                     <div className={`h-12 w-12 rounded-full flex items-center justify-center ${
                         warningStatus === 'red' ? 'bg-red-100 text-red-500' : warningStatus === 'yellow' ? 'bg-yellow-100 text-yellow-600' : 'bg-green-100 text-green-600'
                     }`}>
                        {warningStatus === 'red' ? <AlertTriangle size={24} /> : warningStatus === 'yellow' ? <Activity size={24} /> : <ShieldCheck size={24} />}
                     </div>
                     {/* Dots */}
                    <div className="absolute bottom-3 left-0 right-0 flex justify-center gap-1">
                        <div className="w-1.5 h-1.5 rounded-full bg-zen-200 dark:bg-zen-700"></div>
                        <div className="w-1.5 h-1.5 rounded-full bg-zen-400"></div>
                    </div>
                 </div>
            </div>

        </div>
      </div>

      {/* Calendar */}
      <div className="bg-white dark:bg-zen-900 rounded-2xl p-4 shadow-sm border border-zen-200 dark:border-zen-800">
        
        {/* Calendar Header with Title */}
        <div className="flex flex-col mb-4">
          <span className="text-xs text-zen-400 uppercase tracking-widest font-bold mb-2 ml-1">{txt.calTitle}</span>
          <div className="flex justify-between items-center px-2">
            <button onClick={() => changeMonth(-1)} className="p-2 hover:bg-zen-100 dark:hover:bg-zen-800 rounded-full transition-colors"><ChevronLeft size={20}/></button>
            <h2 className="text-lg font-medium">{monthName}</h2>
            <button onClick={() => changeMonth(1)} className="p-2 hover:bg-zen-100 dark:hover:bg-zen-800 rounded-full transition-colors"><ChevronRight size={20}/></button>
          </div>
        </div>

        <div className="grid grid-cols-7 gap-1 mb-2 text-center">
          {['S','M','T','W','T','F','S'].map((d, i) => (
            <div key={i} className="text-xs text-zen-400 font-bold">{d}</div>
          ))}
        </div>

        <div className="grid grid-cols-7 gap-1">
           {calendarDays.map((date, idx) => {
             if (!date) return <div key={idx} className="h-10" />;
             const dayLogs = getDayLogs(date);
             const hasSolo = dayLogs.some(l => l.type === 'solo');
             const hasPartner = dayLogs.some(l => l.type === 'partner');
             const hasUrge = dayLogs.some(l => l.type === 'urge');
             const isToday = new Date().toDateString() === date.toDateString();

             return (
               <div key={idx} className={`h-12 rounded-xl flex flex-col items-center justify-center relative transition-colors ${isToday ? 'bg-zen-100 dark:bg-zen-800 ring-1 ring-inset ring-accent-500/30' : ''}`}>
                  <span className={`text-[10px] mb-0.5 ${isToday ? 'font-bold text-accent-500' : 'text-zen-400 dark:text-zen-500'}`}>{date.getDate()}</span>
                  <div className="flex items-center justify-center h-4 gap-0.5">
                    {hasPartner ? (
                      <Swords size={12} className="text-red-500 dark:text-red-400" />
                    ) : hasSolo ? (
                      <Joystick size={12} className="text-blue-500 dark:text-blue-400" />
                    ) : hasUrge ? (
                      <Wind size={12} className="text-emerald-500 dark:text-emerald-400" />
                    ) : null}
                  </div>
               </div>
             );
           })}
        </div>
        
        <div className="flex justify-center gap-4 mt-6 text-[10px] text-zen-500 uppercase tracking-wider">
           <div className="flex items-center gap-1">
             <Joystick size={12} className="text-blue-500"/> 
             {txt.solo}
           </div>
           <div className="flex items-center gap-1">
             <Swords size={12} className="text-red-500"/> 
             {txt.partner}
           </div>
           <div className="flex items-center gap-1">
             <Wind size={12} className="text-emerald-500"/> 
             {txt.urgeLabel}
           </div>
        </div>
      </div>

      {/* Quick Tools */}
      <div className="grid grid-cols-2 gap-4">
          <button 
             onClick={() => onChangeView(AppView.BREATHING)} 
             className="bg-white dark:bg-zen-900 rounded-xl p-4 border border-zen-200 dark:border-zen-800 flex items-center gap-3 shadow-sm hover:bg-zen-50 dark:hover:bg-zen-800 transition-colors"
          >
             <div className="p-2 bg-indigo-100 dark:bg-indigo-900/30 text-indigo-500 dark:text-indigo-400 rounded-lg">
                <Wind size={18} />
             </div>
             <div className="text-left">
                <span className="block text-sm font-medium">{txt.urge}</span>
             </div>
          </button>

          <button 
             onClick={() => onChangeView(AppView.JOURNAL)} 
             className="bg-white dark:bg-zen-900 rounded-xl p-4 border border-zen-200 dark:border-zen-800 flex items-center gap-3 shadow-sm hover:bg-zen-50 dark:hover:bg-zen-800 transition-colors"
          >
             <div className="p-2 bg-emerald-100 dark:bg-emerald-900/30 text-emerald-500 dark:text-emerald-400 rounded-lg">
                <Brain size={18} />
             </div>
             <div className="text-left">
                <span className="block text-sm font-medium">{txt.sage}</span>
             </div>
          </button>
      </div>

    </div>
  );
};

export default Dashboard;
