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

var videoInput;
var videoOutput;
var webRtcPeer;
var stream;
var videoIndex = 0
var state = null;
var stompClient;

const I_CAN_START = 0;
const I_CAN_STOP = 1;
const I_AM_STARTING = 2;

window.onload = function() {
	//console = new Console();
	console.log('Page loaded ...');
	$('#add-stream').hide();
	videoInput = document.getElementById('videoInput');
	videoOutput = document.getElementById('videoOutput');

	var socket = new SockJS('/loopback');
	stompClient = Stomp.over(socket);
	stompClient.connect({}, function(frame) {
		setState(I_CAN_START);
		console.log('Connected: ' + frame);

		stompClient.subscribe('/user/topic/errors', onError)
		stompClient.subscribe('/user/queue/ice-candidates', onRemoteIceCandidateMessage);
		stompClient.subscribe('/user/topic/start', onStartResponseMessage);
	});

	setState(I_CAN_START);
}

window.onbeforeunload = function() {
	stop();
	if (stompClient != null) {
		stompClient.disconnect()
	}
}

function onError(error) {
	console.error('ERROR ' + error)	
}

function disconnect() {
	if (stompClient != null) {
		stompClient.disconnect()
	}
	setConnected(false)
	console.log("Disconnected")
}

function onStartResponseMessage(message) { 

	setState(I_CAN_STOP)

	console.info('SDP offer received from server. Processing ...')
	var sdp = JSON.parse(message.body)
	webRtcPeer.processOffer(sdp, function(error, sdpAnswer) {
		if (error) return setState(I_CAN_START)

		stompClient.send('/app/processAnswer', {}, JSON.stringify({ 'sdpAnswer' : sdpAnswer}))
	});
}

function onRemoteIceCandidateMessage(message) {
	var candidate = JSON.parse(message.body)
	console.debug('Adding remote candidate ' + candidate)
	webRtcPeer.addIceCandidate(candidate, function(error) {
		if (error)
			console.error('Error adding candidate: ' + error)
	})
}

function start() {
	console.log('Starting video call ...')

	// Disable start button
	setState(I_AM_STARTING)
	showSpinner(videoInput, videoOutput)
	setState(I_CAN_STOP)
	var options = {
		onicecandidate : onIceCandidate,
		localVideo : videoInput,
		remoteVideo : videoOutput
	}

	webRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerSendrecv(options, function(error) {
		if (error) return setState(I_CAN_START)

		console.info('Sending message to start media session...')
		stompClient.send("/app/start", {})
	})

}

function onIceCandidate(candidate) {
	console.info('Local candidate' + candidate)
	stompClient.send('/app/ice-candidate', {}, JSON.stringify(candidate))
}

function stop(streamDiv) {

	if (webRtcPeer) {

		console.info('Stopping video call ...');
		setState(I_CAN_START);
		webRtcPeer.dispose();
		webRtcPeer = null;
		
		stompClient.send('/app/stop');

		hideSpinner(videoInput, videoOutput);
	}
}

function setState(nextState) {
	switch (nextState) {
	case I_CAN_START:
		$('#start').attr('disabled', false);
		$('#start').attr('onclick', 'start()');
		$('#stop').attr('disabled', true);
		$('#stop').removeAttr('onclick');
		break;

	case I_CAN_STOP:
		$('#start').attr('disabled', true);
		$('#stop').attr('disabled', false);
		$('#stop').attr('onclick', 'stop()');
		break;

	case I_AM_STARTING:
		$('#start').attr('disabled', true);
		$('#start').removeAttr('onclick');
		$('#stop').attr('disabled', true);
		$('#stop').removeAttr('onclick');
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
