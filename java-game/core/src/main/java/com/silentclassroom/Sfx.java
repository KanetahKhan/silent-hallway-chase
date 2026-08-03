package com.silentclassroom;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tiny procedural chiptune sound effects, synthesized at runtime with
 * {@code javax.sound.sampled} — no audio files and no extra dependencies.
 * Ported from the original vp_game ChiptuneSfx and extended with the
 * cues Silent Classroom needs (footsteps, robot stingers, chase music,
 * search/token feedback, mini-game feedback).
 *
 * <p>Everything is best-effort: if there is no audio device (headless box,
 * CI), the first failure disables sound for the session and the game plays
 * on silently. One-shot playback happens on a small bounded thread pool so
 * rapid hits can overlap without blocking the render thread; excess
 * simultaneous sounds are dropped rather than queued.
 */
public final class Sfx {

    private static final float SAMPLE_RATE = 44_100f;
    private static final AudioFormat FORMAT =
            new AudioFormat(SAMPLE_RATE, 16, 1, true, false); // 16-bit mono LE signed

    private static volatile boolean enabled = true;
    private static volatile double masterVolume = 1.0;

    /** Bounded pool: up to a few concurrent SFX, drop the rest (no queue lag). */
    private static final ThreadPoolExecutor POOL = new ThreadPoolExecutor(
            2, 6, 5, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(8),
            r -> { Thread t = new Thread(r, "sfx"); t.setDaemon(true); return t; },
            new ThreadPoolExecutor.DiscardPolicy());

    private Sfx() {}

    private enum Wave { SQUARE, SINE, TRIANGLE, NOISE }

    // ----- Public one-shot cues ----------------------------------------------

    /** Soft short tap for the player's footsteps (alternate pitch per foot). */
    public static void footstep(boolean leftFoot) {
        play(mix(
                sweep(Wave.TRIANGLE, leftFoot ? 200 : 175, 95, 0.05, 0.07),
                tone(Wave.NOISE, 1, 0.02, 0.035)));
    }

    /** Compact metallic step for the hallway sentinel's servo movement. */
    public static void servoStep() {
        play(mix(
                sweep(Wave.SQUARE, 270, 115, 0.075, 0.12),
                tone(Wave.NOISE, 1, 0.025, 0.07)));
    }

    /** Rising two-note "spotted!" sting when the robot enters ALERT. */
    public static void alertSting() {
        play(concat(
                tone(Wave.SQUARE, 440, 0.08, 0.2),
                tone(Wave.SQUARE, 660, 0.14, 0.22)));
    }

    /** Harsh siren-like sting when the robot commits to a CHASE. */
    public static void chaseSting() {
        play(concat(
                sweep(Wave.SQUARE, 500, 900, 0.12, 0.2),
                sweep(Wave.SQUARE, 900, 500, 0.12, 0.2),
                sweep(Wave.SQUARE, 500, 950, 0.14, 0.22)));
    }

    /** Low descending "lost you" cue when the robot drops to SEARCH. */
    public static void searchSting() {
        play(sweep(Wave.TRIANGLE, 400, 150, 0.3, 0.16));
    }

    /** Loud crunchy hit when the robot catches the player. */
    public static void caught() {
        play(concat(
                mix(tone(Wave.NOISE, 1, 0.08, 0.25), tone(Wave.SQUARE, 110, 0.08, 0.3)),
                sweep(Wave.SQUARE, 220, 70, 0.3, 0.28)));
    }

    /** Short latch-and-hinge click for opening or closing a classroom door. */
    public static void door() {
        play(concat(
                tone(Wave.NOISE, 1, 0.025, 0.13),
                sweep(Wave.TRIANGLE, 190, 105, 0.095, 0.18)));
    }

    /** Papery rustle for searching furniture. */
    public static void searchRustle() {
        play(concat(
                tone(Wave.NOISE, 1, 0.06, 0.09),
                tone(Wave.NOISE, 1, 0.05, 0.06),
                tone(Wave.NOISE, 1, 0.07, 0.08)));
    }

    /** Bright rising arpeggio when a glitch token / terminal is found. */
    public static void tokenFound() {
        play(concat(
                tone(Wave.SQUARE, 523, 0.07, 0.16),
                tone(Wave.SQUARE, 659, 0.07, 0.16),
                tone(Wave.SQUARE, 784, 0.07, 0.17),
                tone(Wave.SQUARE, 1047, 0.16, 0.18)));
    }

    /** Soft low whoosh when ducking into / out of a hiding spot. */
    public static void hide(boolean nowHiding) {
        if (nowHiding) play(sweep(Wave.SINE, 300, 90, 0.18, 0.14));
        else           play(sweep(Wave.SINE, 90, 300, 0.15, 0.12));
    }

    /** Ominous low pulse when the robot barges into the room. */
    public static void robotEnters() {
        play(concat(
                tone(Wave.SQUARE, 196, 0.12, 0.2),
                tone(Wave.SQUARE, 175, 0.16, 0.2),
                sweep(Wave.SQUARE, 175, 120, 0.2, 0.22)));
    }

    // ----- Mini-game cues ------------------------------------------------------

    /** Rising blip; pitch climbs with the combo for that "chain" feel. */
    public static void hit(int combo) {
        int c = Math.min(combo, 20);
        double f = 620 + c * 38;
        play(tone(Wave.SQUARE, f, 0.07, 0.16));
    }

    /** Harsh low buzz for a wrong move / mistake. */
    public static void wrongFix() {
        play(tone(Wave.SQUARE, 150, 0.13, 0.22));
    }

    /** Descending thud when a token breaches / is missed. */
    public static void breach() {
        play(sweep(Wave.TRIANGLE, 320, 110, 0.18, 0.28));
    }

    /** Small neutral UI blip (cursor select / pick up). */
    public static void blip() {
        play(tone(Wave.SQUARE, 740, 0.05, 0.1));
    }

    /** Two-note rising fanfare — mini-game victory. */
    public static void miniGameWin() {
        play(concat(
                tone(Wave.SQUARE, 523, 0.08, 0.16),
                tone(Wave.SQUARE, 784, 0.12, 0.18),
                tone(Wave.SQUARE, 1047, 0.2, 0.18)));
    }

    /** Three-note descending sting — mini-game failure. */
    public static void miniGameLose() {
        play(concat(
                tone(Wave.SQUARE, 440, 0.14, 0.18),
                tone(Wave.SQUARE, 349, 0.14, 0.18),
                tone(Wave.SQUARE, 262, 0.26, 0.2)));
    }

    // ----- Chase music loop -----------------------------------------------------

    private static final AtomicBoolean chaseLoopOn = new AtomicBoolean(false);
    private static volatile byte[] chaseLoopPcm;

    /** Start the urgent pulsing chase loop (no-op if already running). */
    public static void startChaseLoop() {
        if (!enabled || !chaseLoopOn.compareAndSet(false, true)) return;
        Thread t = new Thread(Sfx::runChaseLoop, "sfx-chase-loop");
        t.setDaemon(true);
        t.start();
    }

    /** Stop the chase loop after the current bar. */
    public static void stopChaseLoop() {
        chaseLoopOn.set(false);
    }

    public static boolean isChaseLoopRunning() {
        return chaseLoopOn.get();
    }

    /** One tense bar: driving low pulses with an off-beat high stab. */
    private static byte[] buildChaseBar() {
        byte[] p1 = tone(Wave.SQUARE, 165, 0.09, 0.16);
        byte[] p2 = tone(Wave.SQUARE, 220, 0.09, 0.17);
        byte[] rest = silence(0.045);
        byte[] stab = tone(Wave.SQUARE, 660, 0.05, 0.09);
        return concat(
                p1, rest, p2, rest,
                p1, rest, mix(p2, stab), rest,
                p1, rest, p2, rest,
                sweep(Wave.SQUARE, 220, 260, 0.09, 0.17), rest);
    }

    private static void runChaseLoop() {
        byte[] bar = chaseLoopPcm;
        if (bar == null) chaseLoopPcm = bar = buildChaseBar();
        try (SourceDataLine line = AudioSystem.getSourceDataLine(FORMAT)) {
            line.open(FORMAT, Math.max(bar.length, 4096));
            line.start();
            while (chaseLoopOn.get() && enabled) {
                byte[] scaled = scaleVolume(bar, masterVolume * 0.9);
                line.write(scaled, 0, scaled.length);
            }
            line.drain();
            line.stop();
        } catch (Throwable t) {
            enabled = false;
        } finally {
            chaseLoopOn.set(false);
        }
    }

    // ----- Volume ---------------------------------------------------------------

    public static void setMasterVolume(double volume) {
        masterVolume = Double.isFinite(volume)
                ? Math.max(0.0, Math.min(1.0, volume))
                : 1.0;
    }

    public static double getMasterVolume() {
        return masterVolume;
    }

    // ----- Synthesis ----------------------------------------------------------

    private static byte[] tone(Wave wave, double freq, double seconds, double amp) {
        return sweep(wave, freq, freq, seconds, amp);
    }

    private static byte[] silence(double seconds) {
        return new byte[((int) (seconds * SAMPLE_RATE)) * 2];
    }

    /** A single note that glides from {@code f0} to {@code f1} with an envelope. */
    private static byte[] sweep(Wave wave, double f0, double f1, double seconds, double amp) {
        int n = (int) (seconds * SAMPLE_RATE);
        byte[] out = new byte[n * 2];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / n;
            double freq = f0 + (f1 - f0) * t;
            phase += 2 * Math.PI * freq / SAMPLE_RATE;
            double s;
            switch (wave) {
                case SQUARE:   s = Math.sin(phase) >= 0 ? 1 : -1; break;
                case SINE:     s = Math.sin(phase); break;
                case TRIANGLE: s = 2 / Math.PI * Math.asin(Math.sin(phase)); break;
                default:       s = Math.random() * 2 - 1; break; // NOISE
            }
            // Short attack, smooth decay to avoid clicks.
            double env = Math.min(1.0, i / (SAMPLE_RATE * 0.005)) * (1.0 - t);
            short v = (short) (s * env * amp * Short.MAX_VALUE);
            out[i * 2]     = (byte) (v & 0xff);
            out[i * 2 + 1] = (byte) ((v >> 8) & 0xff);
        }
        return out;
    }

    private static byte[] concat(byte[]... parts) {
        int len = 0;
        for (byte[] p : parts) len += p.length;
        byte[] out = new byte[len];
        int o = 0;
        for (byte[] p : parts) { System.arraycopy(p, 0, out, o, p.length); o += p.length; }
        return out;
    }

    /** Sum two buffers sample-by-sample (length = max), clamped. */
    private static byte[] mix(byte[] a, byte[] b) {
        int n = Math.max(a.length, b.length);
        byte[] out = new byte[n - (n % 2)];
        for (int i = 0; i + 1 < out.length; i += 2) {
            int va = i + 1 < a.length ? (short) ((a[i] & 0xff) | (a[i + 1] << 8)) : 0;
            int vb = i + 1 < b.length ? (short) ((b[i] & 0xff) | (b[i + 1] << 8)) : 0;
            int v = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, va + vb));
            out[i]     = (byte) (v & 0xff);
            out[i + 1] = (byte) ((v >> 8) & 0xff);
        }
        return out;
    }

    private static void play(byte[] pcm) {
        if (!enabled) return;
        double volume = masterVolume;
        if (volume <= 0.0) return;

        POOL.execute(() -> {
            byte[] playbackPcm = scaleVolume(pcm, volume);
            try (SourceDataLine line = AudioSystem.getSourceDataLine(FORMAT)) {
                line.open(FORMAT, Math.max(playbackPcm.length, 4096));
                line.start();
                line.write(playbackPcm, 0, playbackPcm.length);
                line.drain();
                line.stop();
            } catch (Throwable t) {
                // No mixer / line unavailable / security — give up on audio quietly.
                enabled = false;
            }
        });
    }

    /** Returns a scaled copy so concurrent cues never mutate shared PCM data. */
    private static byte[] scaleVolume(byte[] pcm, double volume) {
        if (volume >= 1.0) return pcm;

        byte[] out = new byte[pcm.length];
        int evenLength = pcm.length - (pcm.length % 2);
        for (int i = 0; i < evenLength; i += 2) {
            short sample = (short) ((pcm[i] & 0xff) | (pcm[i + 1] << 8));
            short scaled = (short) Math.round(sample * volume);
            out[i] = (byte) (scaled & 0xff);
            out[i + 1] = (byte) ((scaled >> 8) & 0xff);
        }
        if (evenLength < pcm.length) out[evenLength] = pcm[evenLength];
        return out;
    }
}
