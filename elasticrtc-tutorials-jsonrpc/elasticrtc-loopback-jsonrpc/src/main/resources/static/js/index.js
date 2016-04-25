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

var wsUri = 'https://' + location.host + '/loopback';
var jsonrpcClient;
var videoInput;
var videoOutput;
var webRtcPeer;
var stream;
var videoIndex = 0
var state = null;

const I_CAN_START = 0;
const I_CAN_STOP = 1;
const I_AM_STARTING = 2;

window.onload = function() {
	console = new Console();
	console.log('Page loaded ...');
	$('#add-stream').hide();
	videoInput = document.getElementById('videoInput');
	videoOutput = document.getElementById('videoOutput');

	var config = {
			sendCloseMessage : true,
			ws : {
				uri : wsUri,
				useSockJS : true
			},
			rpc : {
				requestTimeout : 15000,
				iceCandidate : remoteIceCandidate
			}
	};

	jsonrpcClient = new RpcBuilder.clients.JsonRpcClient(config);

	setState(I_CAN_START);
}

window.onbeforeunload = function() {
	jsonrpcClient.close()
	stop()
}

function register(callback) {
	jsonrpcClient.send('registerClient', {}, function(error, answer) { 
		if (error) return setState(I_CAN_START)
		setState(I_CAN_STOP)
		callback()
	});
}

function remoteIceCandidate(candidate) {
	webRtcPeer.addIceCandidate(candidate, function(error) {
		if (error)
			return console.error('Error adding candidate: ' + error);
	});
}

function start() {
	console.log('Starting video call ...');

	// Disable start button
	setState(I_AM_STARTING);
	showSpinner(videoInput, videoOutput);

	console.log('Creating WebRtcPeer and generating local sdp offer ...');
	register(function() {

		var options = {
				onicecandidate : onIceCandidate,
				localVideo : videoInput,
				remoteVideo : videoOutput
		}

		webRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerSendrecv(options, negotiateWebRtcWithKurento);
	});
}

function negotiateWebRtcWithKurento(error) {

	if (error) return setState(I_CAN_START)

	jsonrpcClient.send('startMediaSession', {}, function(error, sdpOffer) { 
		if (error) return setState(I_CAN_START)

		setState(I_CAN_STOP)

		console.log('SDP offer received from server. Processing ...');

		webRtcPeer.processOffer(sdpOffer.value, function(error, sdpAnswer) {
			if (error) return console.error(error);
			jsonrpcClient.send('processAnswer', { sdpAnswer : sdpAnswer}, function(error) { 
				if (error) return setState(I_CAN_START)
			});
		});
	});
}

function onOffer(error, offerSdp) {
	if (error)
		return console.error('Error generating the offer');
	console.info('Invoking SDP offer callback function ' + location.host);

}

function onIceCandidate(candidate) {
	console.log('Local candidate' + JSON.stringify(candidate));

	jsonrpcClient.send('iceCandidate', {
		candidate : candidate
	}, function(error, answer) { 
		if (error) return console.error(error)
	});
}

function stop(streamDiv) {

	if (webRtcPeer) {

		console.log('Stopping video call ...');
		setState(I_CAN_START);
		webRtcPeer.dispose();
		webRtcPeer = null;

		jsonrpcClient.send('stopMediaSession', {}, function(error) { 
			if (error) return console.error(error)
		});

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
