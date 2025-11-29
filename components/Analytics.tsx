
import React from 'react';
import { LogEntry, Language } from '../types';
import { BarChart, Bar, XAxis, Tooltip, ResponsiveContainer, Cell, PieChart, Pie, ScatterChart, Scatter, YAxis, ZAxis } from 'recharts';
import { Calendar, Brain, Moon, Activity } from 'lucide-react';

interface AnalyticsProps {
  logs: LogEntry[];
  lang: Language;
}

const t = {
  en: {
    title: "Analysis",
    needLogs: "Log at least 2 entries to unlock insights.",
    cycleForecast: "Cycle Forecast",
    cycleDesc: "Based on your recent rhythm, your next high-focus period is likely around:",
    highDemand: "You are currently in a high-frequency phase.",
    needData: "Need more data",
    heatMap: "Activity Heatmap",
    context: "Context Analysis",
    vsSleep: "Vs Sleep",
    sleepData: "Coming soon with more sleep data.",
    vsStress: "Vs Stress",
    lowStress: "Low Stress",
    highStress: "High Stress",
    avgPleasure: "Avg Pleasure Rating",
    sources: "Sources",
    days: ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'],
    slots: ['Night', 'Morn', 'Noon', 'Eve']
  },
  cn: {
    title: "趋势分析",
    needLogs: "记录至少2条数据以解锁洞察。",
    cycleForecast: "周期预测",
    cycleDesc: "基于您最近的节奏，下一个高需求期可能在：",
    highDemand: "您目前处于高频率阶段。",
    needData: "需要更多数据",
    heatMap: "活动热力图",
    context: "关联分析",
    vsSleep: "对比睡眠",
    sleepData: "需要更多睡眠数据。",
    vsStress: "对比压力",
    lowStress: "低压环境",
    highStress: "高压环境",
    avgPleasure: "平均愉悦度",
    sources: "来源分布",
    days: ['日', '一', '二', '三', '四', '五', '六'],
    slots: ['深夜', '早晨', '午间', '晚间']
  }
};

const Analytics: React.FC<AnalyticsProps> = ({ logs, lang }) => {
  const txt = t[lang];

  if (logs.length < 2) {
      return (
          <div className="flex flex-col items-center justify-center h-64 text-zen-500">
              <Calendar size={48} className="mb-4 opacity-50" />
              <p>{txt.needLogs}</p>
          </div>
      )
  }

  // --- HEATMAP LOGIC ---
  const days = txt.days;
  const timeSlots = txt.slots; // 0-6, 6-12, 12-18, 18-24
  
  // Initialize grid [dayIndex][slotIndex] = count
  const heatGrid = Array(7).fill(0).map(() => Array(4).fill(0));
  
  logs.forEach(l => {
      const d = new Date(l.timestamp);
      const dayIdx = d.getDay();
      const hour = d.getHours();
      const slotIdx = Math.floor(hour / 6);
      heatGrid[dayIdx][slotIdx]++;
  });

  const maxHeat = Math.max(...heatGrid.flat());
  const getOpacity = (val: number) => val === 0 ? 0.1 : 0.3 + (val / maxHeat) * 0.7;

  // --- PREDICTION LOGIC ---
  // Average interval between recent logs (exclude urge surfing for "Demand" prediction, keeping it to actual sessions)
  const sessionLogs = logs.filter(l => l.type !== 'urge').sort((a,b) => b.timestamp - a.timestamp); // Newest first
  
  let nextPredictedDate = null;
  let isHighDemand = false;

  if (sessionLogs.length > 1) {
      let totalInterval = 0;
      let intervalsCount = 0;
      
      for(let i = 0; i < Math.min(sessionLogs.length - 1, 10); i++) {
         const diff = sessionLogs[i].timestamp - sessionLogs[i+1].timestamp;
         totalInterval += diff;
         intervalsCount++;
      }
      
      const avgIntervalMs = intervalsCount > 0 ? totalInterval / intervalsCount : 0;
      nextPredictedDate = avgIntervalMs > 0 ? new Date(sessionLogs[0].timestamp + avgIntervalMs) : null;
      isHighDemand = avgIntervalMs > 0 && avgIntervalMs < (1000 * 60 * 60 * 24 * 2); // Frequent if < 2 days
  }

  // --- CORRELATION LOGIC ---
  // Group by Stress Level
  const stressGroups = { low: { total: 0, count: 0 }, high: { total: 0, count: 0 } };
  sessionLogs.forEach(l => {
      if(l.stressLevel) {
          if(l.stressLevel <= 3) { stressGroups.low.total += l.pleasureRating; stressGroups.low.count++; }
          else { stressGroups.high.total += l.pleasureRating; stressGroups.high.count++; }
      }
  });
  
  const avgPleasureLowStress = stressGroups.low.count ? (stressGroups.low.total / stressGroups.low.count).toFixed(1) : '-';
  const avgPleasureHighStress = stressGroups.high.count ? (stressGroups.high.total / stressGroups.high.count).toFixed(1) : '-';

  // --- BASIC CHARTS DATA ---
  const stimuliCounts: Record<string, number> = {};
  logs.forEach(l => {
      const s = l.stimuli;
      if (Array.isArray(s)) {
          s.forEach(item => stimuliCounts[item] = (stimuliCounts[item] || 0) + 1);
      } else {
          // Skip 'none' counting for clean chart
          if (s !== 'none') {
             stimuliCounts[s] = (stimuliCounts[s] || 0) + 1;
          }
      }
  });
  const stimuliData = Object.entries(stimuliCounts).map(([name, value]) => ({ name, value }));
  const COLORS = ['#3b82f6', '#64748b', '#94a3b8', '#cbd5e1', '#e2e8f0'];

  return (
    <div className="space-y-6 pb-20">
      
      {/* PREDICTION CARD */}
      <h2 className="text-2xl font-light mb-6">{txt.title}</h2>
      <div className="bg-white dark:bg-gradient-to-br dark:from-zen-900 dark:to-zen-950 p-6 rounded-2xl border border-zen-200 dark:border-zen-800 shadow-lg">
          <div className="flex items-center gap-2 mb-2">
            <Brain className="text-accent-500" size={20}/>
            <h3 className="text-zen-900 dark:text-zen-200 font-medium">{txt.cycleForecast}</h3>
          </div>
          <p className="text-sm text-zen-500 dark:text-zen-400 mb-4">
             {txt.cycleDesc}
          </p>
          <div className="text-2xl text-zen-900 dark:text-white font-light">
             {nextPredictedDate ? nextPredictedDate.toLocaleDateString(lang === 'cn' ? 'zh-CN' : undefined, {weekday: 'long', month: 'short', day: 'numeric'}) : txt.needData}
          </div>
          {isHighDemand && <p className="text-xs text-accent-400 mt-2">{txt.highDemand}</p>}
      </div>

      {/* HEATMAP */}
      <div className="bg-white dark:bg-zen-900 p-6 rounded-2xl border border-zen-200 dark:border-zen-800 shadow-sm">
        <h3 className="text-zen-900 dark:text-zen-200 font-medium mb-4">{txt.heatMap}</h3>
        <div className="grid grid-cols-[auto_1fr] gap-4">
            {/* Y-Axis Labels */}
            <div className="flex flex-col justify-between py-2 text-xs text-zen-500">
                {days.map(d => <span key={d}>{d}</span>)}
            </div>
            
            {/* Grid */}
            <div className="grid grid-rows-7 gap-1">
                {heatGrid.map((dayRow, dIdx) => (
                    <div key={dIdx} className="grid grid-cols-4 gap-1 h-6">
                        {dayRow.map((val, tIdx) => (
                            <div 
                                key={tIdx} 
                                className="rounded-sm bg-accent-500 transition-all hover:ring-2 ring-zen-400 dark:ring-white/20"
                                style={{ opacity: getOpacity(val) }}
                                title={`${days[dIdx]} ${timeSlots[tIdx]}: ${val}`}
                            />
                        ))}
                    </div>
                ))}
                {/* X-Axis Labels */}
                <div className="grid grid-cols-4 mt-2 text-xs text-zen-500 text-center">
                    {timeSlots.map(t => <span key={t}>{t}</span>)}
                </div>
            </div>
        </div>
      </div>

      {/* CORRELATION */}
      <div className="bg-white dark:bg-zen-900 p-6 rounded-2xl border border-zen-200 dark:border-zen-800 shadow-sm">
        <h3 className="text-zen-900 dark:text-zen-200 font-medium mb-4">{txt.context}</h3>
        <div className="grid grid-cols-2 gap-4">
            <div className="bg-zen-50 dark:bg-zen-950 p-3 rounded-xl border border-zen-200 dark:border-zen-800">
                <div className="flex items-center gap-2 mb-2 text-zen-400 text-xs uppercase tracking-wider">
                    <Moon size={14}/>
                    <span>{txt.vsSleep}</span>
                </div>
                <div className="text-xs text-zen-500">
                    {txt.sleepData}
                </div>
            </div>
            <div className="bg-zen-50 dark:bg-zen-950 p-3 rounded-xl border border-zen-200 dark:border-zen-800">
                <div className="flex items-center gap-2 mb-2 text-zen-400 text-xs uppercase tracking-wider">
                    <Activity size={14}/>
                    <span>{txt.vsStress}</span>
                </div>
                <div className="space-y-2">
                    <div className="flex justify-between text-xs">
                        <span className="text-zen-500">{txt.lowStress}</span>
                        <span className="text-accent-400 font-bold">{avgPleasureLowStress}</span>
                    </div>
                    <div className="flex justify-between text-xs">
                        <span className="text-zen-500">{txt.highStress}</span>
                        <span className="text-red-400 font-bold">{avgPleasureHighStress}</span>
                    </div>
                    <div className="text-[10px] text-zen-600 mt-1 text-right">{txt.avgPleasure}</div>
                </div>
            </div>
        </div>
      </div>

      {/* STIMULI PIE */}
      <div className="bg-white dark:bg-zen-900 p-6 rounded-2xl border border-zen-200 dark:border-zen-800 shadow-sm">
        <h3 className="text-zen-900 dark:text-zen-200 font-medium mb-2">{txt.sources}</h3>
        <div className="h-48 w-full flex items-center justify-center">
            <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                    <Pie data={stimuliData} innerRadius={40} outerRadius={70} paddingAngle={5} dataKey="value" stroke="none">
                        {stimuliData.map((entry, index) => (
                            <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                        ))}
                    </Pie>
                    <Tooltip contentStyle={{ backgroundColor: '#1e293b', border: 'none', borderRadius: '8px' }} />
                </PieChart>
            </ResponsiveContainer>
        </div>
      </div>

    </div>
  );
};

export default Analytics;
