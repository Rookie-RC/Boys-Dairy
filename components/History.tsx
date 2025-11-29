
import React, { useState, useRef, useEffect } from 'react';
import { Search, Filter, Trash2, Pin, User, Heart, ChevronDown, Check, Wind } from 'lucide-react';
import { LogEntry, Language, Mood, ActivityType } from '../types';

interface HistoryProps {
  logs: LogEntry[];
  lang: Language;
  onDelete: (id: string) => void;
  onTogglePin: (id: string) => void;
}

const t = {
  en: {
    title: "History",
    search: "Search notes...",
    allTypes: "All Types",
    solo: "Solo",
    partner: "Partner",
    urge: "Urge Surf",
    allMoods: "All Moods",
    pin: "Pin",
    delete: "Delete",
    noLogs: "No records found matching filters.",
    moodLabels: {
        Anxious: "Anxious",
        Bored: "Bored",
        Excited: "Excited",
        Stressed: "Stressed",
        Neutral: "Neutral",
        Relaxed: "Relaxed",
        Guilty: "Guilty",
        Empty: "Empty",
        Energetic: "Energetic"
    }
  },
  cn: {
    title: "历史记录",
    search: "搜索笔记...",
    allTypes: "所有类型",
    solo: "单人",
    partner: "伴侣",
    urge: "冲动抑制",
    allMoods: "所有情绪",
    pin: "置顶",
    delete: "删除",
    noLogs: "未找到匹配记录",
    moodLabels: {
        Anxious: "焦虑",
        Bored: "无聊",
        Excited: "兴奋",
        Stressed: "压力",
        Neutral: "平淡",
        Relaxed: "放松",
        Guilty: "内疚",
        Empty: "空虚",
        Energetic: "精力充沛"
    }
  }
};

const getMoodColor = (mood: Mood) => {
    switch(mood) {
        case 'Relaxed': return 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400';
        case 'Energetic': return 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400';
        case 'Excited': return 'bg-cyan-100 text-cyan-700 dark:bg-cyan-900/30 dark:text-cyan-400';
        case 'Neutral': return 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-400';
        case 'Bored': return 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400';
        case 'Anxious': return 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400';
        case 'Stressed': return 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400';
        case 'Guilty': return 'bg-rose-100 text-rose-700 dark:bg-rose-900/30 dark:text-rose-400';
        case 'Empty': return 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400';
        default: return 'bg-gray-100 text-gray-700';
    }
};

const History: React.FC<HistoryProps> = ({ logs, lang, onDelete, onTogglePin }) => {
  const [filterType, setFilterType] = useState<ActivityType | 'all'>('all');
  const [filterMood, setFilterMood] = useState<Mood | 'all'>('all');
  const [searchTerm, setSearchTerm] = useState('');
  const [activeItem, setActiveItem] = useState<string | null>(null);
  
  // Custom Dropdown States
  const [showTypeMenu, setShowTypeMenu] = useState(false);
  const [showMoodMenu, setShowMoodMenu] = useState(false);
  const typeMenuRef = useRef<HTMLDivElement>(null);
  const moodMenuRef = useRef<HTMLDivElement>(null);

  const txt = t[lang];

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
        if (typeMenuRef.current && !typeMenuRef.current.contains(event.target as Node)) {
            setShowTypeMenu(false);
        }
        if (moodMenuRef.current && !moodMenuRef.current.contains(event.target as Node)) {
            setShowMoodMenu(false);
        }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const filteredLogs = logs.filter(log => {
    const matchesType = filterType === 'all' || log.type === filterType;
    const matchesMood = filterMood === 'all' || log.moodPost === filterMood;
    const matchesSearch = log.journal?.toLowerCase().includes(searchTerm.toLowerCase()) || false;
    return matchesType && matchesMood && (searchTerm === '' || matchesSearch);
  }).sort((a, b) => {
    if (a.pinned && !b.pinned) return -1;
    if (!a.pinned && b.pinned) return 1;
    return b.timestamp - a.timestamp;
  });

  const moods: Mood[] = ['Relaxed', 'Guilty', 'Empty', 'Energetic', 'Neutral', 'Anxious', 'Bored', 'Excited', 'Stressed'];

  // Custom Dropdown Component
  const DropdownButton = ({ label, isOpen, onClick }: { label: string, isOpen: boolean, onClick: () => void }) => (
    <button 
        onClick={onClick}
        className={`flex items-center gap-2 px-4 py-2 rounded-full text-sm font-medium border transition-all ${isOpen ? 'bg-zen-800 border-accent-500 text-white' : 'bg-white dark:bg-zen-900 border-zen-200 dark:border-zen-800 text-zen-600 dark:text-zen-300'}`}
    >
        {label}
        <ChevronDown size={14} className={`transition-transform ${isOpen ? 'rotate-180' : ''}`} />
    </button>
  );

  return (
    <div className="pb-24 pt-6 px-6 h-full flex flex-col animate-fade-in text-zen-900 dark:text-zen-100">
      <h1 className="text-2xl font-light mb-6">{txt.title}</h1>

      {/* Filters */}
      <div className="flex flex-wrap gap-3 mb-4 relative z-20">
         {/* Type Filter */}
         <div className="relative shrink-0" ref={typeMenuRef}>
            <DropdownButton 
                label={filterType === 'all' ? txt.allTypes : (filterType === 'solo' ? txt.solo : filterType === 'partner' ? txt.partner : txt.urge)} 
                isOpen={showTypeMenu} 
                onClick={() => { setShowTypeMenu(!showTypeMenu); setShowMoodMenu(false); }}
            />
            {showTypeMenu && (
                <div className="absolute top-full mt-2 left-0 w-32 bg-white dark:bg-zen-900 border border-zen-200 dark:border-zen-800 rounded-xl shadow-xl overflow-hidden animate-fade-in flex flex-col z-50">
                    <button onClick={() => {setFilterType('all'); setShowTypeMenu(false)}} className="px-4 py-3 text-left text-sm hover:bg-zen-100 dark:hover:bg-zen-800 text-zen-700 dark:text-zen-200">{txt.allTypes}</button>
                    <button onClick={() => {setFilterType('solo'); setShowTypeMenu(false)}} className="px-4 py-3 text-left text-sm hover:bg-zen-100 dark:hover:bg-zen-800 text-zen-700 dark:text-zen-200">{txt.solo}</button>
                    <button onClick={() => {setFilterType('partner'); setShowTypeMenu(false)}} className="px-4 py-3 text-left text-sm hover:bg-zen-100 dark:hover:bg-zen-800 text-zen-700 dark:text-zen-200">{txt.partner}</button>
                    <button onClick={() => {setFilterType('urge'); setShowTypeMenu(false)}} className="px-4 py-3 text-left text-sm hover:bg-zen-100 dark:hover:bg-zen-800 text-zen-700 dark:text-zen-200">{txt.urge}</button>
                </div>
            )}
         </div>

         {/* Mood Filter */}
         <div className="relative shrink-0" ref={moodMenuRef}>
            <DropdownButton 
                label={filterMood === 'all' ? txt.allMoods : txt.moodLabels[filterMood as Mood]} 
                isOpen={showMoodMenu} 
                onClick={() => { setShowMoodMenu(!showMoodMenu); setShowTypeMenu(false); }}
            />
            {showMoodMenu && (
                <div className="absolute top-full mt-2 left-0 w-40 bg-white dark:bg-zen-900 border border-zen-200 dark:border-zen-800 rounded-xl shadow-xl overflow-hidden animate-fade-in flex flex-col z-50">
                    <button onClick={() => {setFilterMood('all'); setShowMoodMenu(false)}} className="px-4 py-3 text-left text-sm hover:bg-zen-100 dark:hover:bg-zen-800 text-zen-700 dark:text-zen-200">{txt.allMoods}</button>
                    {moods.map(m => (
                        <button key={m} onClick={() => {setFilterMood(m); setShowMoodMenu(false)}} className="px-4 py-3 text-left text-sm hover:bg-zen-100 dark:hover:bg-zen-800 text-zen-700 dark:text-zen-200">{txt.moodLabels[m]}</button>
                    ))}
                </div>
            )}
         </div>
      </div>

      <div className="relative mb-6 z-10">
         <Search size={16} className="absolute left-4 top-3.5 text-zen-500" />
         <input 
            type="text" 
            placeholder={txt.search}
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full bg-zen-100 dark:bg-zen-900 rounded-xl py-3 pl-10 pr-4 text-sm focus:outline-none focus:ring-2 ring-accent-500/50 text-zen-900 dark:text-zen-100 placeholder-zen-500"
         />
      </div>

      {/* List */}
      <div className="flex-1 overflow-y-auto no-scrollbar space-y-3 z-0">
         {filteredLogs.length > 0 ? filteredLogs.map(log => (
            <div 
               key={log.id} 
               className={`relative overflow-hidden rounded-xl border transition-all ${activeItem === log.id ? 'border-accent-500 ring-1 ring-accent-500' : 'border-zen-200 dark:border-zen-800'}`}
               onClick={() => setActiveItem(activeItem === log.id ? null : log.id)}
            >
               <div className="bg-white dark:bg-zen-900 p-4 relative z-10">
                  <div className="flex justify-between items-start mb-2">
                     <div className="flex items-center gap-3">
                        <div className={`p-2 rounded-full ${
                             log.type === 'solo' ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-500' : 
                             log.type === 'partner' ? 'bg-red-100 dark:bg-red-900/30 text-red-500' :
                             'bg-emerald-100 dark:bg-emerald-900/30 text-emerald-500'
                        }`}>
                           {log.type === 'solo' ? <User size={16} /> : log.type === 'partner' ? <Heart size={16} /> : <Wind size={16} />}
                        </div>
                        <div>
                           <div className="text-sm font-medium">{new Date(log.timestamp).toLocaleDateString()}</div>
                           <div className="flex items-center gap-2 mt-0.5">
                               <span className="text-xs text-zen-500">{new Date(log.timestamp).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'})}</span>
                               <span className={`text-[10px] px-2 py-0.5 rounded-full font-medium ${getMoodColor(log.moodPost)}`}>{txt.moodLabels[log.moodPost]}</span>
                           </div>
                        </div>
                     </div>
                     {log.pinned && <Pin size={14} className="text-accent-500 rotate-45" />}
                  </div>
                  {log.journal && (
                     <p className="text-sm text-zen-600 dark:text-zen-300 bg-zen-50 dark:bg-zen-950 p-2 rounded-lg mt-2 line-clamp-2">
                        {log.journal}
                     </p>
                  )}
               </div>

               {/* Action Drawer (revealed on click) */}
               {activeItem === log.id && (
                  <div className="flex bg-zen-100 dark:bg-zen-950 p-2 gap-2 animate-fade-in">
                     <button 
                        onClick={(e) => { e.stopPropagation(); onTogglePin(log.id); setActiveItem(null); }}
                        className="flex-1 py-2 rounded-lg bg-zen-200 dark:bg-zen-800 text-zen-600 dark:text-zen-300 text-xs font-medium flex items-center justify-center gap-1"
                     >
                        <Pin size={14} /> {txt.pin}
                     </button>
                     <button 
                        onClick={(e) => { e.stopPropagation(); onDelete(log.id); setActiveItem(null); }}
                        className="flex-1 py-2 rounded-lg bg-red-100 dark:bg-red-900/30 text-red-500 text-xs font-medium flex items-center justify-center gap-1"
                     >
                        <Trash2 size={14} /> {txt.delete}
                     </button>
                  </div>
               )}
            </div>
         )) : (
            <div className="text-center py-10 text-zen-500 text-sm">
               {txt.noLogs}
            </div>
         )}
      </div>

    </div>
  );
};

export default History;
