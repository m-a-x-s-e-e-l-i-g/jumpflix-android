// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.player

import org.json.JSONObject
import tv.jumpflix.data.PlaybackSource

/** Official provider embeds; no extraction, rehosting, or hidden provider controls. */
internal fun embedDocument(source: PlaybackSource, startSeconds: Long): String {
    val id = JSONObject.quote(source.providerId ?: "")
    val start = startSeconds.coerceAtLeast(0)
    val vimeoHash = source.vimeoPrivacyHash?.let { "&h=$it" }.orEmpty()
    val script = if (source.kind == "youtube") """
        <div id="player"></div><script src="https://www.youtube.com/iframe_api"></script>
        <script>
        var player; window.jumpflixState = {};
        function updateState(){if(!player||!player.getCurrentTime||!window.jumpflixState.ready)return;
          var s=window.jumpflixState;
          s.position=player.getCurrentTime();s.duration=player.getDuration();
          s.paused=player.getPlayerState()!==1;s.rate=player.getPlaybackRate();s.muted=player.isMuted();
          s.rates=player.getAvailablePlaybackRates();
        }
        function onYouTubeIframeAPIReady() {
          player = new YT.Player('player', {width:'100%', height:'100%', videoId:$id,
            playerVars:{autoplay:1, playsinline:1, start:$start, origin:'https://www.jumpflix.tv'},
            events:{onReady:function(){window.jumpflixState.ready=true;if(window.jumpflixWantsPause)player.pauseVideo();updateState();},
              onStateChange:updateState,onPlaybackRateChange:updateState,
              onError:function(){window.jumpflixState.error=true;window.jumpflixState.ready=false;}}
          });
        }
        setInterval(updateState,500);
        window.jumpflixCommand=function(command){
          if(command.name==='pause')window.jumpflixWantsPause=true;
          if(!player||!window.jumpflixState.ready)return;
          if(command.name==='pause')player.pauseVideo();
          else if(command.name==='toggle'){player.getPlayerState()===1?player.pauseVideo():player.playVideo();}
          else if(command.name==='mute'){player.isMuted()?player.unMute():player.mute();}
          else if(command.name==='rate'&&player.getAvailablePlaybackRates().indexOf(command.value)!==-1)player.setPlaybackRate(command.value);
          else if((command.name==='seek'||command.name==='relative')&&Number.isFinite(command.value)){
            var target=command.name==='seek'?command.value:player.getCurrentTime()+command.value;
            player.seekTo(Math.min(player.getDuration(),Math.max(0,target)),true);
          }
          updateState();
        };
        </script>
    """.trimIndent() else """
        <script>
        window.jumpflixState={};
        function jumpflixVimeoError(error){
          if(error&&['PrivacyError','PasswordError'].indexOf(error.name)===-1&&['setPlaybackRate','setMuted','setCurrentTime','play','pause'].indexOf(error.method)!==-1){window.jumpflixState.controlError=true;return;}
          window.jumpflixState.error=true;window.jumpflixState.ready=false;window.jumpflixState.errorName=(error&&error.name)||'Error';
        }
        window.addEventListener('message',function(event){
          var frame=document.getElementById('player');
          if(event.origin!=='https://player.vimeo.com'||!frame||event.source!==frame.contentWindow)return;
          var data=event.data;try{if(typeof data==='string')data=JSON.parse(data);}catch(e){return;}
          if(data&&data.event==='error')jumpflixVimeoError(data.data);
        });
        </script>
        <iframe id="player" title="Vimeo video player" src="https://player.vimeo.com/video/${source.providerId}?autoplay=1${vimeoHash}#t=${start}s" allow="autoplay; fullscreen; picture-in-picture" allowfullscreen referrerpolicy="strict-origin-when-cross-origin"></iframe>
        <script src="https://player.vimeo.com/api/player.js"></script><script>
        var player=new Vimeo.Player('player');
        player.on('timeupdate',function(data){window.jumpflixState.position=data.seconds;window.jumpflixState.duration=data.duration;});
        player.on('play',function(){window.jumpflixState.paused=false;});
        player.on('pause',function(){window.jumpflixState.paused=true;});
        player.on('ended',function(){window.jumpflixState.paused=true;});
        player.on('playbackratechange',function(data){window.jumpflixState.rate=data.playbackRate;});
        player.on('volumechange',function(){player.getMuted().then(function(v){window.jumpflixState.muted=v;}).catch(function(){});});
        player.on('error',jumpflixVimeoError);
        player.ready().catch(jumpflixVimeoError);
        player.ready().then(function(){
          var s=window.jumpflixState;if(s.error)return;s.ready=true;s.rates=[0.5,0.75,1,1.25,1.5,2];
          if(window.jumpflixWantsPause)player.pause().catch(function(){});
          return Promise.all([
            player.getDuration().then(function(v){s.duration=v;}),
            player.getCurrentTime().then(function(v){s.position=v;}),
            player.getPaused().then(function(v){s.paused=v;}),
            player.getPlaybackRate().then(function(v){s.rate=v;}),
            player.getMuted().then(function(v){s.muted=v;})
          ]);
        }).catch(function(){});
        window.jumpflixCommand=function(command){
          if(command.name==='pause')window.jumpflixWantsPause=true;
          if(!window.jumpflixState.ready)return;
          window.jumpflixState.controlError=false;
          var action;
          if(command.name==='pause')action=player.pause();
          else if(command.name==='toggle')action=player.getPaused().then(function(p){return p?player.play():player.pause();});
          else if(command.name==='mute')action=player.getMuted().then(function(v){return player.setMuted(!v);});
          else if(command.name==='rate'&&Number.isFinite(command.value))action=player.setPlaybackRate(command.value);
          else if(command.name==='seek'&&Number.isFinite(command.value))action=player.setCurrentTime(Math.max(0,Math.min(window.jumpflixState.duration||Infinity,command.value)));
          else if(command.name==='relative'&&Number.isFinite(command.value))action=player.getCurrentTime().then(function(t){return player.setCurrentTime(Math.max(0,Math.min(window.jumpflixState.duration||Infinity,t+command.value)));});
          if(action)action.catch(function(error){
            if(error&&['PrivacyError','PasswordError'].indexOf(error.name)!==-1)jumpflixVimeoError(error);
            else window.jumpflixState.controlError=true;
          });
        };
        </script>
    """.trimIndent()
    return """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="referrer" content="strict-origin-when-cross-origin"><style>html,body,#player{margin:0;width:100%;height:100%;background:#000;border:0;overflow:hidden}</style></head><body>$script</body></html>"""
}
