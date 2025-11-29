
import React, { useState, useEffect } from 'react';
import { Wind, ArrowLeft, Check } from 'lucide-react';
import { generateMeditationGuide } from '../services/geminiService';
import { Language, LogEntry, Mood } from '../types';

interface BreathingProps {
  onExit: () => void;
  onSave: (entry: LogEntry) => void;
  lang: Language;
}

const t = {
    en: {
        title: "Urge Surfing",
        inhale: "Inhale",
        hold: "Hold",
        exhale: "Exhale",
        loading: "Loading guidance...",
        time: "Time Remaining",
        finish: "I feel calm now",
        fallback: "Observe the feeling. Let it pass like a cloud.",
        howFeel: "How do you feel now?",
        save: "Save",
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
        title: "冲动抑制",
        inhale: "吸气",
        hold: "屏气",
        exhale: "呼气",
        loading: "正在生成引导...",
        time: "剩余时间",
        finish: "我已冷静",
        fallback: "观察当下的感受，让它像云一样飘过。",
        howFeel: "现在感觉如何？",
        save: "完成",
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
}

const Breathing: React.FC<BreathingProps> = ({ onExit, onSave, lang }) => {
  const [phase, setPhase] = useState<'inhale' | 'hold' | 'exhale'>('inhale');
  const [timeLeft, setTimeLeft] = useState(30); // 30 seconds urge surf
  const [guideText, setGuideText] = useState("");
  const [isFinished, setIsFinished] = useState(false);
  const [selectedMood, setSelectedMood] = useState<Mood | null>(null);

  const txt = t[lang];

  useEffect(() => {
    let timer: number;
    
    // Cycle breathing: Inhale 4s, Hold 4s, Exhale 4s
    const cycle = () => {
      setPhase('inhale');
      setTimeout(() => {
        setPhase('hold');
        setTimeout(() => {
          setPhase('exhale');
        }, 4000);
      }, 4000);
    };

    if (!isFinished) {
        cycle();
        const interval = setInterval(cycle, 12000); // 4+4+4 = 12s cycle
        return () => clearInterval(interval);
    }
  }, [isFinished]);

  useEffect(() => {
    if (isFinished) return;
    const countdown = setInterval(() => {
      setTimeLeft((prev) => {
        if (prev <= 1) return 0;
        return prev - 1;
      });
    }, 1000);
    return () => clearInterval(countdown);
  }, [isFinished]);

  useEffect(() => {
      setGuideText(txt.loading);
      generateMeditationGuide().then(text => {
          if (!text || text.includes("Observe")) {
               setGuideText(txt.fallback);
          } else {
              setGuideText(text);
          }
      });
  }, [lang]);

  const handleFinish = () => {
      setIsFinished(true);
  };

  const handleSaveLog = () => {
      if (!selectedMood) return;

      const entry: LogEntry = {
          id: crypto.randomUUID(),
          timestamp: Date.now(),
          durationSeconds: 30, // Fixed session time
          type: 'urge',
          stimuli: 'none',
          moodPre: 'Anxious', // Assumption for urge surfing
          moodPost: selectedMood,
          pleasureRating: 0,
          journal: ''
      };
      onSave(entry);
  };

  const getCircleSize = () => {
    if (phase === 'inhale') return 'scale-150';
    if (phase === 'hold') return 'scale-150'; // Stay expanded
    return 'scale-100';
  };

  const getPhaseText = () => {
    if (phase === 'inhale') return txt.inhale;
    if (phase === 'hold') return txt.hold;
    return txt.exhale;
  };

  if (isFinished) {
      return (
          <div className="flex flex-col items-center justify-center min-h-screen bg-zen-950 p-6 text-zen-100 animate-fade-in">
              <h2 className="text-2xl font-light mb-8">{txt.howFeel}</h2>
              <div className="flex flex-wrap gap-3 justify-center max-w-xs mb-12">
                   {['Relaxed', 'Neutral', 'Energetic', 'Anxious', 'Stressed'].map(m => (
                      <button 
                        key={m} 
                        onClick={() => setSelectedMood(m as Mood)}
                        className={`px-5 py-3 rounded-full border text-sm transition-all ${selectedMood === m ? 'bg-accent-600 border-accent-600 text-white shadow-lg scale-105' : 'bg-zen-900 border-zen-800 text-zen-400 hover:bg-zen-800'}`}
                      >
                        {txt.moods[m as keyof typeof txt.moods]}
                      </button>
                   ))}
              </div>
              <button 
                onClick={handleSaveLog} 
                disabled={!selectedMood}
                className={`px-8 py-3 rounded-xl font-bold transition-all flex items-center gap-2 ${selectedMood ? 'bg-white text-zen-950 hover:bg-zen-200' : 'bg-zen-800 text-zen-600 cursor-not-allowed'}`}
              >
                  <Check size={20}/>
                  {txt.save}
              </button>
          </div>
      )
  }

  return (
    <div className="flex flex-col items-center justify-center min-h-screen bg-zen-950 relative overflow-hidden p-6 text-zen-100">
      <button 
        onClick={onExit}
        className="absolute top-6 left-6 p-2 text-zen-400 hover:text-white"
      >
        <ArrowLeft />
      </button>

      {/* Ambient Background */}
      <div className={`absolute w-96 h-96 bg-accent-900 rounded-full blur-[100px] transition-all duration-[4000ms] ${getCircleSize()}`} />

      <div className="z-10 text-center space-y-12">
        <h2 className="text-2xl font-light text-zen-100 tracking-widest uppercase">{txt.title}</h2>
        
        <div className="relative flex items-center justify-center">
            {/* Breathing Circle */}
            <div className={`w-48 h-48 rounded-full border-2 border-accent-500/30 flex items-center justify-center transition-all duration-[4000ms] ease-in-out ${getCircleSize()}`}>
                <div className={`w-40 h-40 rounded-full bg-accent-500/10 backdrop-blur-sm flex items-center justify-center`}>
                     <span className="text-2xl font-medium text-white tracking-widest">{getPhaseText()}</span>
                </div>
            </div>
        </div>

        <div className="max-w-md mx-auto min-h-[4rem]">
            <p className="text-zen-300 text-lg leading-relaxed italic animate-pulse">
                "{guideText}"
            </p>
        </div>

        <div className="mt-8">
            <p className="text-zen-500 text-sm uppercase tracking-widest">{txt.time}</p>
            <p className="text-3xl font-mono text-zen-200">{timeLeft}s</p>
        </div>

        {timeLeft === 0 && (
            <button onClick={handleFinish} className="px-8 py-3 bg-zen-800 rounded-full text-zen-100 hover:bg-zen-700 transition-colors border border-zen-700">
                {txt.finish}
            </button>
        )}
      </div>
    </div>
  );
};

export default Breathing;
