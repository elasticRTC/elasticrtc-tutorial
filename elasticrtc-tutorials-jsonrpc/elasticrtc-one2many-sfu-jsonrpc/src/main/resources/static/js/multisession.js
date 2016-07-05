/*
 * (C) Copyright 2014 Kurento (http://kurento.org/)
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 */

var wsUri = 'https://' + location.host + '/sfu-multisession';
var videoFeed;
var webRtcPeers = {};
var stream;
var streamIndex = 0
var viewerVideoIndex = 0
var state = null;
var simulcast

const I_CAN_START = 0;
const I_CAN_STOP = 1;
const I_AM_STARTING = 2;

window.onload = function() {
	console = new Console();
	console.log('Page loaded ...');
	$('#add-stream').hide();
	videoFeed = document.getElementById('videoFeed');

	var config = {
			sendCloseMessage : true,
			ws : {
				uri : wsUri,
				useSockJS : true
			},
			rpc : {
				requestTimeout : 15000,
				iceCandidate : remoteOnIceCandidate
			}
	};

	jsonrpcClient = new RpcBuilder.clients.JsonRpcClient(config);

	setState(I_CAN_START);
}

window.onbeforeunload = function() {
	stop()
	jsonrpcClient.close()
}

function register(callback) {
	jsonrpcClient.send('register', { simulcast : simulcast }, function(error, answer) { 
		if (error) return setState(I_CAN_START)
		setState(I_CAN_STOP)
		callback(answer.type)
	});
}

function remoteOnIceCandidate(candidateAndUserId) {
	var webRtcPeer = webRtcPeers[candidateAndUserId.userId]
	webRtcPeer.addIceCandidate(candidateAndUserId.candidate, function(error) {
		if (error)
			return console.error('Error adding candidate: ' + error);
	});
}

function start() {
	console.log('Starting video call ...');

	// Disable start button
	setState(I_AM_STARTING);
	showSpinner(videoFeed);

	simulcast = $('#simulcast-checkbox').is(':checked')

	console.log('Creating WebRtcPeer and generating local sdp offer ...')
	register(function() {

		var constraints = {
				audio: true,
				video: {
					width: 1280,
					height: 720
				}
		}

		var options = {
				simulcast : simulcast,
				mediaConstraints : constraints,
				onicecandidate : onIceCandidate,
				onaddstream : onAddStream,
				id : 'presenter'
		}

		//$('#add-stream').show();
		options.localVideo = videoFeed
		options.simulcast = simulcast
		var webRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerSendonly(options,onWebRtcPeerCreated)
		webRtcPeers['presenter'] = webRtcPeer
	});
}

function onWebRtcPeerCreated(error) {

	if (error) return setState(I_CAN_START)

	var webRtcPeer = this

	if (webRtcPeer.id === 'presenter') {
		webRtcPeer.generateOffer(function(error, sdpOffer) {
			negotiateWebRtc(webRtcPeer, { userId : webRtcPeer.id, sdpOffer : sdpOffer }, function(answer) {
				webRtcPeer.processAnswer(answer.sdp, function(error) {
					if (error) return setState(I_CAN_START)
				})

			})
		}) 
	} else {
		negotiateWebRtc(webRtcPeer, { userId : webRtcPeer.id }, function(answer) {
			webRtcPeer.processOffer(answer.sdp, function(error, sdpAnswer) {
				if (error) return setState(I_CAN_START)
				jsonrpcClient.send('processAnswer', {
					sdpAnswer : sdpAnswer,
					userId : webRtcPeer.id
				}, function(error) {
					if (error) return setState(I_CAN_START)
				});
			});
		});
	}
}

function negotiateWebRtc(webRtcPeer, params, callback) {
	
	jsonrpcClient.send('negotiateWebRtc', params, function(error, answer) { 
		if (error) return setState(I_CAN_START)

		setState(I_CAN_STOP)

		console.log('SDP received from server. Processing ...');

		callback(answer)
	});
}

function onAddStream(event) {
	if (!stream) stream = event.stream
}

function onOffer(error, offerSdp) {
	if (error) return console.error('Error generating the offer')
	console.info('Invoking SDP offer callback function ' + location.host)
}

function onIceCandidate(candidate) {
	console.log('Local candidate' + JSON.stringify(candidate))

	jsonrpcClient.send('iceCandidate', {
		candidate : candidate,
		userId : this.id
	}, function(error, answer) { 
		if (error) return console.error(error)
	})
}

function stop(viewerDiv) {

	if (viewerDiv) {
		var video = viewerDiv.getElementsByTagName('video')[0]
		webRtcPeers[viewerDiv.id].dispose()
		delete webRtcPeers[viewerDiv.id]
		document.getElementById('viewers').removeChild(viewerDiv)

		jsonrpcClient.send('stopUserSession', { userId : viewerDiv.id }, function(error) { 
			if (error) return console.error(error)
		})
	} else {

		console.log('Stopping video call ...')
		setState(I_CAN_START)
		clearWebRtcPeers()
		jsonrpcClient.send('stop', {}, function(error) { 
			if (error) return console.error(error)
		})

		hideSpinner(videoFeed)
		viewerVideoIndex = 0
		streamIndex = 0
	}

}

function clearWebRtcPeers() {
	for (var key in webRtcPeers) {
		if (key !== 'presenter') {
			stop(document.getElementById(key))
		} else {
			webRtcPeers[key].dispose()
			delete webRtcPeers[key]
		}

	}
}

function addStream() {
	var streamId = 'stream' + streamIndex
	var videoId = 'video' + streamIndex
	var newDiv = document.createElement('div')
	newDiv.id = streamId 
	//newDiv.addProperty('class', 'row')
	newDiv.className = 'row';
	newDiv.innerHTML = 
		'	<div class="col-md-5">' +
		'		<h3>Local stream ' + streamIndex +'</h3>' +
		'		<video id="' + videoId + '" autoplay width="480px" height="270px"' +
		'			poster="img/webrtc.png"></video>' +
		'	</div>' +
		'   <div class="col-md-2">' +
		'		<a href="#" class="btn btn-danger" onclick="stop(' + streamId + ')">' +
		'      		<span class="glyphicon glyphicon-stop"></span> Stop </a>' +
		'	</div>';
	document.getElementById('streams').appendChild(newDiv)
	cloneInputStream(videoId)
	streamIndex++
}

function addViewer() {
	var viewerId = 'viewer' + viewerVideoIndex
	var videoId = 'videoViewer' + viewerVideoIndex
	var newDiv = document.createElement('div')
	newDiv.id = viewerId
	newDiv.className = 'row';
	newDiv.innerHTML = 
		'	<div class="col-md-5">' +
		'		<h3>Viewer ' + viewerVideoIndex +'</h3>' +
		'		<video id="' + videoId + '" autoplay width="480px" height="270px"' +
		'			poster="img/webrtc.png"></video>' +
		'	</div>' +
		'   <div class="col-md-2">' +
		'		<a href="#" class="btn btn-danger" id="stop'+ viewerId + '" onclick="stop(' + viewerId + ')">' +
		'      		<span class="glyphicon glyphicon-stop"></span> Stop </a>' +
		'	</div>' +
		'   <div class="col-md-3">' +
		'		<input type="radio" checked name="quality-' + viewerId + '" ' +
		'			id="high-quality-' + viewerId + '" value="high" ' +
		'			onclick="switchQuality(' + viewerId + ')"> High quality<br>' +
		'		<input type="radio" name="quality-' + viewerId + '" ' +
		'			id="low-quality-' + viewerId + '" value="low" ' +
		'			onclick="switchQuality(' + viewerId + ')"> Low quality<br>' +
		'	</div>';
	document.getElementById('viewers').appendChild(newDiv)

	viewerVideoIndex++
	//$('#add-stream').hide();
	var constraints = {
		audio: true,
		video: {
			width: 1280,
			height: 720
		}
	}

	var options = {
		mediaConstraints : constraints,
		onicecandidate : onIceCandidate,
		onaddstream : onAddStream,
		remoteVideo : document.getElementById(videoId),
		id : viewerId
	}
	var webRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options, onWebRtcPeerCreated)

	webRtcPeers[webRtcPeer.id] = webRtcPeer

}

function cloneInputStream(videoId) {
//	var videoTag = document.getElementById(videoId)
//	var newLocalStream = stream.clone()
//	videoTag.temp = newLocalStream
//	webRtcPeer.peerConnection.addStream(newLocalStream)
}

function switchQuality(viewerDiv) {
	jsonrpcClient.send('switchQuality', { userId : viewerDiv.id }, function(error) { 
		if (error) return console.error(error)
	})
}

function setState(nextState) {
	switch (nextState) {
	case I_CAN_START:
		$('#simulcast-checkbox').attr('disabled', false);
		$('#simulcast-label').removeClass('disabled');
		$('#start').attr('disabled', false);
		$('#start').attr('onclick', 'start()');
		$('#stop').attr('disabled', true);
		$('#stop').removeAttr('onclick');
		$('#add-stream').attr('disabled', true);
		$('#add-stream').removeAttr('onclick');
		$('#add-viewer').attr('disabled', true);
		$('#add-viewer').removeAttr('onclick');

		clearWebRtcPeers()

		break;

	case I_CAN_STOP:
		$('#simulcast-checkbox').attr('disabled', true);
		$('#simulcast-label').addClass('disabled');
		$('#start').attr('disabled', true);
		$('#stop').attr('disabled', false);
		$('#stop').attr('onclick', 'stop()');
		$('#add-stream').attr('disabled', false);
		$('#add-stream').attr('onclick', 'addStream()');
		$('#add-viewer').attr('disabled', false);
		$('#add-viewer').attr('onclick', 'addViewer()');
		break;

	case I_AM_STARTING:
		$('#simulcast-checkbox').attr('disabled', true);
		$('#simulcast-label').addClass('disabled');
		$('#start').attr('disabled', true);
		$('#start').removeAttr('onclick');
		$('#stop').attr('disabled', true);
		$('#stop').removeAttr('onclick');
		$('#add-stream').attr('disabled', true);
		$('#add-stream').removeAttr('onclick');
		$('#add-viewer').attr('disabled', true);
		$('#add-viewer').removeAttr('onclick');
		break;

	default:
		onError('Unknown state ' + nextState);
	return;
	}
	state = nextState;
}

function showSpinner() {
	for (var i = 0; i < arguments.length; i++) {
		arguments[i].poster = './img/transparent-1px.png';
		arguments[i].style.background = "center transparent url('./img/spinner.gif') no-repeat";
	}
}

function hideSpinner() {
	for (var i = 0; i < arguments.length; i++) {
		arguments[i].src = '';
		arguments[i].poster = './img/webrtc.png';
		arguments[i].style.background = '';
	}
}
/**
 * Lightbox utility (to display media pipeline image in a modal dialog)
 */
$(document).delegate('*[data-toggle="lightbox"]', 'click', function(event) {
	event.preventDefault();
	$(this).ekkoLightbox();
});
