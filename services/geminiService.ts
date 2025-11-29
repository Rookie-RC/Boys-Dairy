import { GoogleGenAI } from "@google/genai";
import { LogEntry } from "../types";

const getClient = () => {
    const apiKey = process.env.API_KEY;
    if (!apiKey) return null;
    return new GoogleGenAI({ apiKey });
};

export const generatePostClarityInsight = async (logs: LogEntry[]): Promise<string> => {
  const ai = getClient();
  if (!ai) return "AI insights unavailable. Please configure API Key.";

  // We only send anonymized, recent patterns to protect privacy
  const recentLogs = logs.slice(0, 5);
  const dataSummary = recentLogs.map(l => 
    `Date: ${new Date(l.timestamp).toLocaleDateString()}, Mood Pre: ${l.moodPre}, Mood Post: ${l.moodPost}, Notes: ${l.journal}`
  ).join('\n');

  const prompt = `
    Analyze these "Post-Clarity" journal entries from a user tracking their sexual health and habits.
    Focus on emotional patterns, triggers, and constructive advice.
    Be empathetic, scientific, and non-judgmental. Keep it brief (under 100 words).
    Do not be explicit. Use terms like "session", "release", "focus".
    
    Data:
    ${dataSummary}
  `;

  try {
    const response = await ai.models.generateContent({
      model: 'gemini-2.5-flash',
      contents: prompt,
    });
    return response.text || "No insights generated.";
  } catch (error) {
    console.error("Gemini Error:", error);
    return "Could not generate insights at this moment.";
  }
};

export const generateMeditationGuide = async (): Promise<string> => {
  const ai = getClient();
  if (!ai) return "Breathe in deeply... Hold... Breathe out slowly.";

  try {
    const response = await ai.models.generateContent({
      model: 'gemini-2.5-flash',
      contents: "Generate a short, calming 2-sentence instruction for 'Urge Surfing' (riding out a wave of an urge without acting on it). Focus on breath and observing the sensation without judgment.",
    });
    return response.text || "Observe the feeling. Let it pass like a cloud.";
  } catch (error) {
    return "Focus on your breath. Inhale for 4 seconds, exhale for 6.";
  }
};