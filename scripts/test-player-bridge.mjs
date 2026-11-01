// SPDX-License-Identifier: GPL-3.0-only
// Execute the actual inline provider scripts with SDK doubles; no network required.
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import test from 'node:test';
import assert from 'node:assert/strict';

const kotlin = readFileSync(new URL('../app/src/main/java/tv/jumpflix/player/EmbedDocument.kt', import.meta.url), 'utf8');
const templates = kotlin.match(/if \(source.kind == "youtube"\) """([\s\S]*?)"""\.trimIndent\(\) else """([\s\S]*?)"""\.trimIndent/);
assert.ok(templates, 'Provider templates found');
function document(kind, sdk) {
  const frame = { contentWindow: {} };
  const listeners = {};
  const sandbox = { window: {}, document: { getElementById: () => frame }, setInterval() {}, ...sdk };
  sandbox.window.addEventListener = (name, callback) => { listeners[name] = callback; };
  const context = vm.createContext(sandbox);
  const html = templates[kind === 'youtube' ? 1 : 2]
    .replaceAll('$id', '"testVideoId"').replaceAll('$start', '0')
    .replaceAll('${source.providerId}', '123456').replaceAll('${vimeoHash}', '').replaceAll('${start}', '0');
  for (const script of html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)) vm.runInContext(script[1], context);
  return { ...sandbox, listeners, frame };
}
const flush = () => new Promise(resolve => setImmediate(resolve));

test('YouTube controls use supported rates, clamp seeks, and reflect player state', () => {
  let options;
  const video = { position: 20, duration: 60, state: 1, muted: false, rate: 1,
    getCurrentTime() { return this.position; }, getDuration() { return this.duration; },
    getPlayerState() { return this.state; }, getPlaybackRate() { return this.rate; },
    isMuted() { return this.muted; }, getAvailablePlaybackRates() { return [1, 2]; },
    pauseVideo() { this.state = 2; }, playVideo() { this.state = 1; },
    seekTo(value) { this.position = value; }, setPlaybackRate(value) { this.rate = value; },
    mute() { this.muted = true; }, unMute() { this.muted = false; }
  };
  const page = document('youtube', { YT: { Player: function (_, config) { options = config; return video; } } });
  page.onYouTubeIframeAPIReady(); options.events.onReady();
  const command = (name, value) => page.window.jumpflixCommand({ name, value });
  command('toggle'); assert.equal(video.state, 2); assert.equal(page.window.jumpflixState.paused, true);
  command('relative', -100); assert.equal(video.position, 0);
  command('seek', 100); assert.equal(video.position, 60);
  command('rate', 1.5); assert.equal(video.rate, 1);
  command('rate', 2); assert.equal(video.rate, 2);
  command('mute'); assert.equal(page.window.jumpflixState.muted, true);
});

function vimeoPage() {
  const events = {};
  const video = { position: 20, duration: 60, paused: false, muted: false,
    on(name, callback) { events[name] = callback; }, ready: () => Promise.resolve(),
    getDuration() { return Promise.resolve(this.duration); }, getCurrentTime() { return Promise.resolve(this.position); },
    getPaused() { return Promise.resolve(this.paused); }, getMuted() { return Promise.resolve(this.muted); },
    getPlaybackRate: () => Promise.resolve(1),
    setCurrentTime(value) { this.position = value; return Promise.resolve(value); },
    setMuted(value) { this.muted = value; events.volumechange(); return Promise.resolve(value); },
    play() { this.paused = false; events.play(); return Promise.resolve(); },
    pause() { this.paused = true; events.pause(); return Promise.resolve(); },
    setPlaybackRate: () => Promise.reject(new Error('Owner disabled speed'))
  };
  return { page: document('vimeo', { Vimeo: { Player: function () { return video; } } }), video };
}

test('Vimeo mute, seeking, and unavailable speed keep playback state intact', async () => {
  const { page, video } = vimeoPage(); await flush();
  const command = async (name, value) => { page.window.jumpflixCommand({ name, value }); await flush(); };
  await command('toggle'); assert.equal(page.window.jumpflixState.paused, true);
  await command('relative', 100); assert.equal(video.position, 60);
  await command('seek', -100); assert.equal(video.position, 0);
  await command('mute'); assert.equal(page.window.jumpflixState.muted, true);
  await command('rate', 2);
  assert.equal(page.window.jumpflixState.controlError, true);
  assert.equal(page.window.jumpflixState.error, undefined);
  assert.equal(page.window.jumpflixState.ready, true);
});

test('Vimeo ignores spoofed messages but reports early owner privacy failures', async () => {
  const { page } = vimeoPage();
  const message = { event: 'error', data: { name: 'PrivacyError', method: 'play' } };
  page.listeners.message({ origin: 'https://evil.test', source: page.frame.contentWindow, data: message });
  assert.equal(page.window.jumpflixState.error, undefined);
  page.listeners.message({ origin: 'https://player.vimeo.com', source: {}, data: message });
  assert.equal(page.window.jumpflixState.error, undefined);
  page.listeners.message({ origin: 'https://player.vimeo.com', source: page.frame.contentWindow, data: JSON.stringify(message) });
  await flush();
  assert.equal(page.window.jumpflixState.errorName, 'PrivacyError');
  assert.equal(page.window.jumpflixState.ready, false);
});

test('An early lifecycle pause survives Vimeo SDK initialization', async () => {
  const { page, video } = vimeoPage();
  page.window.jumpflixCommand({ name: 'pause' }); await flush();
  assert.equal(video.paused, true);
});
