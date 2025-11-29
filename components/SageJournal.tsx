
import React, { useState } from 'react';
import { ArrowLeft, Plus, Save, Book } from 'lucide-react';
import { LogEntry, Language } from '../types';

interface SageJournalProps {
  logs: LogEntry[];
  onAddNote: (note: string) => void;
  onBack: () => void;
  lang: Language;
}

const t = {
    en: {
        title: "Sage Mode Journal",
        cardTitle: "Post-Clarity Insight",
        cardDesc: "Capture your clearest thoughts immediately after a session.",
        btnWrite: "Write New Insight",
        past: "Past Reflections",
        noEntries: "No journal entries yet.",
        placeholder: "What's on your mind right now? (Tasks, revelations, feelings...)",
        btnSave: "Save Reflection"
    },
    cn: {
        title: "贤者笔记",
        cardTitle: "贤者时刻洞察",
        cardDesc: "趁现在头脑清醒，记录下你的灵感或反思。",
        btnWrite: "记录新感悟",
        past: "往期回顾",
        noEntries: "暂无笔记。",
        placeholder: "此刻你在想什么？(待办事项、感悟、情绪...)",
        btnSave: "保存笔记"
    }
};

const SageJournal: React.FC<SageJournalProps> = ({ logs, onAddNote, onBack, lang }) => {
  const [newNote, setNewNote] = useState('');
  const [isWriting, setIsWriting] = useState(false);
  const txt = t[lang];

  // Filter only logs that have journal entries
  const journalEntries = logs.filter(l => l.journal && l.journal.trim().length > 0);

  const handleSave = () => {
    if (newNote.trim()) {
      onAddNote(newNote);
      setNewNote('');
      setIsWriting(false);
    }
  };

  return (
    <div className="min-h-screen bg-zen-950 p-6 flex flex-col animate-fade-in pb-24 text-zen-100">
      <div className="flex items-center gap-4 mb-8">
        <button onClick={onBack} className="p-2 text-zen-400 hover:text-white bg-zen-900 rounded-full">
          <ArrowLeft size={20} />
        </button>
        <h1 className="text-xl font-light text-zen-100">{txt.title}</h1>
      </div>

      {!isWriting ? (
        <div className="flex-1">
          {/* CTA Card */}
          <div className="bg-gradient-to-r from-zen-800 to-zen-900 p-6 rounded-2xl mb-8 border border-zen-700 shadow-lg">
             <div className="flex justify-between items-start">
                <div>
                   <h3 className="text-lg font-medium text-white mb-2">{txt.cardTitle}</h3>
                   <p className="text-sm text-zen-400 mb-4 max-w-[200px]">
                      {txt.cardDesc}
                   </p>
                </div>
                <Book className="text-accent-500 opacity-50" size={48} />
             </div>
             <button 
                onClick={() => setIsWriting(true)}
                className="w-full py-3 bg-accent-600 hover:bg-accent-500 text-white rounded-xl font-medium transition-colors flex items-center justify-center gap-2"
             >
                <Plus size={18} />
                {txt.btnWrite}
             </button>
          </div>

          <h3 className="text-sm text-zen-500 uppercase tracking-widest mb-4">{txt.past}</h3>
          <div className="space-y-4">
             {journalEntries.length > 0 ? journalEntries.map(entry => (
                <div key={entry.id} className="bg-zen-900 p-5 rounded-xl border border-zen-800">
                   <div className="flex justify-between mb-2">
                      <span className="text-xs text-zen-500">{new Date(entry.timestamp).toLocaleDateString()}</span>
                      <span className="text-xs px-2 py-0.5 bg-zen-800 rounded-full text-zen-400 lowercase">{entry.moodPost}</span>
                   </div>
                   <p className="text-zen-200 text-sm leading-relaxed whitespace-pre-wrap">
                      {entry.journal}
                   </p>
                </div>
             )) : (
                <div className="text-center py-12 text-zen-600 italic">
                   {txt.noEntries}
                </div>
             )}
          </div>
        </div>
      ) : (
        <div className="flex-1 flex flex-col">
           <textarea
             value={newNote}
             onChange={(e) => setNewNote(e.target.value)}
             placeholder={txt.placeholder}
             className="flex-1 w-full bg-zen-900/50 border border-zen-800 rounded-2xl p-6 text-zen-200 focus:outline-none focus:border-accent-500 resize-none placeholder-zen-600 mb-4 text-lg font-light leading-relaxed"
             autoFocus
           />
           <button 
             onClick={handleSave}
             className="w-full py-4 bg-white text-zen-950 rounded-xl font-bold hover:bg-zen-200 transition-colors flex items-center justify-center gap-2"
           >
             <Save size={20} />
             {txt.btnSave}
           </button>
        </div>
      )}
    </div>
  );
};

export default SageJournal;
