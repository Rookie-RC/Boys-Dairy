
import React, { useState } from 'react';
import { X, Check, Clock, Brain, Heart, Zap, BookOpen, Film, Music, User, Activity, Droplets } from 'lucide-react';
import { LogEntry, Stimuli, Mood, ActivityType, Language, Volume } from '../types';

interface LoggerProps {
  onSave: (entry: LogEntry) => void;
  onCancel: () => void;
  lang: Language;
}

const t = {
  en: {
    title: "New Entry",
    context: "Context",
    solo: "Solo",
    partner: "Partner",
    stress: "Stress Level",
    stressLevels: ['Zen', 'Low', 'Mod', 'High', 'Max'],
    duration: "Duration (Minutes)",
    stimuli: "Stimulus (Select one)",
    moodPre: "Pre-Session Mood",
    moodPost: "Post-Session Mood",
    pleasure: "Pleasure (1-10)",
    volume: "Release Volume",
    volumes: { low: "Low", medium: "Medium", high: "High" },
    sageTitle: "Sage Time Journal",
    sageDesc: "Capture your thoughts, ideas, or feelings while your mind is clear.",
    sagePlaceholder: "I'm feeling... I realized...",
    nextFeelings: "Next: Feelings",
    nextReflection: "Next: Reflection",
    save: "Save Entry",
    stimuliLabels: {
        imagination: "Mind",
        video: "Video",
        audio: "Audio",
        reading: "Text",
        none: "None"
    },
    moods: {
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
    title: "新增记录",
    context: "场景",
    solo: "单人",
    partner: "伴侣",
    stress: "压力水平",
    stressLevels: ['禅', '低', '中', '高', '极高'],
    duration: "持续时间 (分钟)",
    stimuli: "辅助介质 (单选)",
    moodPre: "事前情绪",
    moodPost: "事后情绪",
    pleasure: "愉悦度 (1-10)",
    volume: "释放量",
    volumes: { low: "少", medium: "中", high: "多" },
    sageTitle: "贤者笔记",
    sageDesc: "趁现在头脑清醒，记录下你的灵感或反思。",
    sagePlaceholder: "我现在感觉... 我意识到...",
    nextFeelings: "下一步: 感受",
    nextReflection: "下一步: 反思",
    save: "保存记录",
    stimuliLabels: {
        imagination: "想象",
        video: "视频",
        audio: "音频",
        reading: "阅读",
        none: "无"
    },
    moods: {
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

const Logger: React.FC<LoggerProps> = ({ onSave, onCancel, lang }) => {
  const [step, setStep] = useState(1);
  const [timestamp] = useState(Date.now());
  const [duration, setDuration] = useState(10);
  const [type, setType] = useState<ActivityType>('solo');
  const [stimuli, setStimuli] = useState<Stimuli>('none');
  const [moodPre, setMoodPre] = useState<Mood>('Neutral');
  const [moodPost, setMoodPost] = useState<Mood>('Relaxed');
  const [pleasure, setPleasure] = useState(5);
  const [volume, setVolume] = useState<Volume>('medium');
  const [journal, setJournal] = useState('');
  
  // Context
  const [stressLevel, setStressLevel] = useState(3);

  const txt = t[lang];

  const handleSave = () => {
    const entry: LogEntry = {
      id: crypto.randomUUID(),
      timestamp,
      durationSeconds: duration * 60,
      type,
      stimuli,
      moodPre,
      moodPost,
      pleasureRating: pleasure,
      volume,
      journal,
      stressLevel
    };
    onSave(entry);
  };

  const StepIndicator = () => (
    <div className="flex gap-2 mb-6 justify-center">
      {[1, 2, 3].map(i => (
        <div key={i} className={`h-1 w-8 rounded-full transition-colors ${i <= step ? 'bg-accent-500' : 'bg-zen-200 dark:bg-zen-800'}`} />
      ))}
    </div>
  );

  return (
    <div className="min-h-screen bg-white dark:bg-zen-950 p-6 flex flex-col animate-fade-in text-zen-900 dark:text-zen-100 transition-colors">
      <div className="flex justify-between items-center mb-6">
        <button onClick={onCancel} className="p-2 bg-zen-100 dark:bg-zen-900 rounded-full text-zen-500 dark:text-zen-400 hover:text-zen-900 dark:hover:text-white"><X size={20} /></button>
        <h2 className="text-lg font-medium">{txt.title}</h2>
        <div className="w-10" />
      </div>

      <StepIndicator />

      <div className="flex-1 overflow-y-auto no-scrollbar pb-20">
        
        {step === 1 && (
          <div className="space-y-8">
            {/* Context Section */}
            <div>
               <h3 className="text-zen-500 uppercase tracking-widest text-xs mb-4">{txt.context}</h3>
               <div className="grid grid-cols-2 gap-4">
                  <button 
                    onClick={() => setType('solo')} 
                    className={`p-4 rounded-xl border flex flex-col items-center gap-2 transition-all active:scale-95 ${type === 'solo' ? 'bg-accent-50 dark:bg-accent-900/20 border-accent-500 text-accent-600 dark:text-accent-400 shadow-md shadow-accent-500/10' : 'bg-zen-50 dark:bg-zen-900 border-zen-200 dark:border-zen-800 text-zen-400'}`}
                  >
                    <User size={24} />
                    <span>{txt.solo}</span>
                  </button>
                  <button 
                    onClick={() => setType('partner')} 
                    className={`p-4 rounded-xl border flex flex-col items-center gap-2 transition-all active:scale-95 ${type === 'partner' ? 'bg-accent-50 dark:bg-accent-900/20 border-accent-500 text-accent-600 dark:text-accent-400 shadow-md shadow-accent-500/10' : 'bg-zen-50 dark:bg-zen-900 border-zen-200 dark:border-zen-800 text-zen-400'}`}
                  >
                    <Heart size={24} />
                    <span>{txt.partner}</span>
                  </button>
               </div>
            </div>

            {/* Stress Level - Enhanced Slider */}
            <div>
                <div className="flex justify-between mb-4">
                    <span className="text-xs text-zen-500 uppercase flex items-center gap-1"><Activity size={12}/> {txt.stress}</span>
                </div>
                
                <div className="relative pt-2 pb-6 px-1">
                    <input 
                    type="range" min="1" max="5" step="1"
                    value={stressLevel} 
                    onChange={(e) => setStressLevel(parseInt(e.target.value))}
                    className="w-full accent-red-500 h-1 hover:h-2 active:h-3 transition-all duration-200 bg-zen-200 dark:bg-zen-800 rounded-lg appearance-none cursor-pointer relative z-10"
                    />
                    <div className="flex justify-between mt-3 text-[10px] text-zen-400 uppercase tracking-wider font-medium select-none">
                        {txt.stressLevels.map((level, idx) => (
                            <span 
                                key={idx} 
                                className={`transition-colors ${stressLevel === idx + 1 ? 'text-red-500 font-bold scale-110' : ''}`}
                            >
                                {level}
                            </span>
                        ))}
                    </div>
                </div>
            </div>

            {/* Duration - Enhanced Slider */}
            <div>
              <h3 className="text-zen-500 uppercase tracking-widest text-xs mb-4">{txt.duration}</h3>
              <div className="flex items-center justify-between bg-zen-50 dark:bg-zen-900 p-4 rounded-xl border border-zen-200 dark:border-zen-800">
                <Clock className="text-zen-400" />
                <input 
                  type="range" 
                  min="1" max="120" 
                  value={duration} 
                  onChange={(e) => setDuration(parseInt(e.target.value))}
                  className="w-full mx-4 accent-accent-500 h-2 hover:h-3 active:h-4 transition-all duration-200 bg-zen-200 dark:bg-zen-800 rounded-lg appearance-none cursor-pointer"
                />
                <span className="text-xl font-mono w-12 text-right">{duration}</span>
              </div>
            </div>

            {/* Stimuli */}
            <div>
               <h3 className="text-zen-500 uppercase tracking-widest text-xs mb-4">{txt.stimuli}</h3>
               <div className="grid grid-cols-3 gap-3">
                 {[
                   { id: 'imagination', icon: Brain, label: txt.stimuliLabels.imagination },
                   { id: 'video', icon: Film, label: txt.stimuliLabels.video },
                   { id: 'audio', icon: Music, label: txt.stimuliLabels.audio },
                   { id: 'reading', icon: BookOpen, label: txt.stimuliLabels.reading },
                   { id: 'none', icon: Zap, label: txt.stimuliLabels.none }
                 ].map((item) => (
                   <button
                     key={item.id}
                     onClick={() => setStimuli(item.id as Stimuli)}
                     className={`p-3 rounded-lg border text-sm flex flex-col items-center gap-2 transition-all active:scale-95 ${stimuli === item.id ? 'bg-zen-800 border-accent-500 text-white shadow-md' : 'bg-zen-50 dark:bg-zen-900/50 border-zen-200 dark:border-zen-800 text-zen-500'}`}
                   >
                     <item.icon size={20} />
                     {item.label}
                   </button>
                 ))}
               </div>
            </div>
            
            <button onClick={() => setStep(2)} className="w-full py-4 bg-zen-900 dark:bg-zen-100 text-white dark:text-zen-950 font-bold rounded-xl mt-8 hover:opacity-90 transition-opacity">
              {txt.nextFeelings}
            </button>
          </div>
        )}

        {step === 2 && (
          <div className="space-y-8">
             <div>
                <h3 className="text-zen-500 uppercase tracking-widest text-xs mb-4">{txt.moodPre}</h3>
                <div className="flex flex-wrap gap-2">
                   {['Anxious', 'Bored', 'Excited', 'Stressed', 'Neutral'].map(m => (
                      <button 
                        key={m} 
                        onClick={() => setMoodPre(m as Mood)}
                        className={`px-4 py-2 rounded-full border text-sm transition-all ${moodPre === m ? 'bg-accent-600 border-accent-600 text-white shadow-md scale-105' : 'bg-zen-50 dark:bg-zen-900 border-zen-200 dark:border-zen-800 text-zen-500'}`}
                      >
                        {txt.moods[m as keyof typeof txt.moods]}
                      </button>
                   ))}
                </div>
             </div>

             <div>
                <h3 className="text-zen-500 uppercase tracking-widest text-xs mb-4">{txt.moodPost}</h3>
                <div className="flex flex-wrap gap-2">
                   {['Relaxed', 'Guilty', 'Empty', 'Energetic', 'Neutral'].map(m => (
                      <button 
                        key={m} 
                        onClick={() => setMoodPost(m as Mood)}
                        className={`px-4 py-2 rounded-full border text-sm transition-all ${moodPost === m ? 'bg-accent-600 border-accent-600 text-white shadow-md scale-105' : 'bg-zen-50 dark:bg-zen-900 border-zen-200 dark:border-zen-800 text-zen-500'}`}
                      >
                        {txt.moods[m as keyof typeof txt.moods]}
                      </button>
                   ))}
                </div>
             </div>

             <div>
                <h3 className="text-zen-500 uppercase tracking-widest text-xs mb-4">{txt.pleasure}</h3>
                <div className="flex justify-between items-center mb-2">
                    <span className="text-xs text-zen-500">1</span>
                    <span className="text-xl font-bold text-accent-500">{pleasure}</span>
                    <span className="text-xs text-zen-500">10</span>
                </div>
                <input 
                  type="range" 
                  min="1" max="10" 
                  value={pleasure} 
                  onChange={(e) => setPleasure(parseInt(e.target.value))}
                  className="w-full accent-accent-500 h-2 bg-zen-200 dark:bg-zen-800 rounded-lg appearance-none cursor-pointer"
                />
             </div>

             {/* Volume Selector */}
             <div>
                <h3 className="text-zen-500 uppercase tracking-widest text-xs mb-4">{txt.volume}</h3>
                <div className="grid grid-cols-3 gap-3">
                    {/* LOW */}
                    <button
                        onClick={() => setVolume('low')}
                        className={`py-4 rounded-xl border flex flex-col items-center justify-center gap-2 transition-all active:scale-95 ${volume === 'low' ? 'bg-zen-800 border-accent-500 text-white shadow-md' : 'bg-zen-50 dark:bg-zen-900 border-zen-200 dark:border-zen-800 text-zen-500'}`}
                    >
                        <div className="flex items-end gap-1 h-6">
                             <div className="w-1.5 h-2 bg-current rounded-full opacity-60"></div>
                             <div className="w-1.5 h-2 bg-zen-300 dark:bg-zen-700 rounded-full opacity-30"></div>
                             <div className="w-1.5 h-2 bg-zen-300 dark:bg-zen-700 rounded-full opacity-30"></div>
                        </div>
                        <span className="text-xs font-medium">{txt.volumes.low}</span>
                    </button>

                    {/* MEDIUM */}
                    <button
                        onClick={() => setVolume('medium')}
                        className={`py-4 rounded-xl border flex flex-col items-center justify-center gap-2 transition-all active:scale-95 ${volume === 'medium' ? 'bg-zen-800 border-accent-500 text-white shadow-md' : 'bg-zen-50 dark:bg-zen-900 border-zen-200 dark:border-zen-800 text-zen-500'}`}
                    >
                        <div className="flex items-end gap-1 h-6">
                             <div className="w-1.5 h-3 bg-current rounded-full opacity-80"></div>
                             <div className="w-1.5 h-4 bg-current rounded-full opacity-80"></div>
                             <div className="w-1.5 h-2 bg-zen-300 dark:bg-zen-700 rounded-full opacity-30"></div>
                        </div>
                        <span className="text-xs font-medium">{txt.volumes.medium}</span>
                    </button>

                    {/* HIGH */}
                    <button
                        onClick={() => setVolume('high')}
                        className={`py-4 rounded-xl border flex flex-col items-center justify-center gap-2 transition-all active:scale-95 ${volume === 'high' ? 'bg-zen-800 border-accent-500 text-white shadow-md' : 'bg-zen-50 dark:bg-zen-900 border-zen-200 dark:border-zen-800 text-zen-500'}`}
                    >
                        <div className="flex items-end gap-1 h-6">
                             <div className="w-1.5 h-3 bg-current rounded-full"></div>
                             <div className="w-1.5 h-5 bg-current rounded-full"></div>
                             <div className="w-1.5 h-6 bg-current rounded-full"></div>
                        </div>
                        <span className="text-xs font-medium">{txt.volumes.high}</span>
                    </button>
                </div>
             </div>

             <button onClick={() => setStep(3)} className="w-full py-4 bg-zen-900 dark:bg-zen-100 text-white dark:text-zen-950 font-bold rounded-xl mt-8 hover:opacity-90 transition-opacity">
               {txt.nextReflection}
             </button>
          </div>
        )}

        {step === 3 && (
          <div className="space-y-6">
            <div className="bg-zen-50 dark:bg-zen-900/50 p-6 rounded-2xl border border-zen-200 dark:border-zen-800 text-center">
               <Brain className="mx-auto text-accent-500 mb-4" size={32} />
               <h3 className="text-xl font-light mb-2">{txt.sageTitle}</h3>
               <p className="text-sm text-zen-500 mb-6">{txt.sageDesc}</p>
               
               <textarea
                 value={journal}
                 onChange={(e) => setJournal(e.target.value)}
                 placeholder={txt.sagePlaceholder}
                 className="w-full h-40 bg-white dark:bg-zen-950 border border-zen-200 dark:border-zen-800 rounded-xl p-4 text-zen-900 dark:text-zen-200 focus:outline-none focus:border-accent-500 resize-none placeholder-zen-400 dark:placeholder-zen-600"
               />
            </div>

            <button onClick={handleSave} className="w-full py-4 bg-accent-600 text-white font-bold rounded-xl hover:bg-accent-500 transition-colors flex items-center justify-center gap-2 shadow-lg shadow-accent-500/20">
               <Check size={20} />
               {txt.save}
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

export default Logger;
