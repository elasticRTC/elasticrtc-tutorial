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

var wsUri = 'https://' + location.host + '/peerconnection';
var videoFeed;
var webRtcPeer;
var stream;
var newStreams = 0
var state = null;
var simulcast;
var isPresenter = false;

var constraints = {
	    audio: true,
	    video: {
	        width: 1280,
	        height: 720
	    }
	}

const I_CAN_START = 0;
const I_CAN_STOP = 1;
const I_AM_STARTING = 2;
const STREAMS_ADDED = 3;

window.onload = function() {
	console.log('Page loaded ...');
	videoFeed = document.getElementById('videoFeed');

	var config = {
			sendCloseMessage : true,
			ws : {
				uri : wsUri,
				useSockJS : true
			},
			rpc : {
				requestTimeout : 15000,
				iceCandidate : remoteOnIceCandidate,
				viewerNegotiation : viewerNegotiation
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
	jsonrpcClient.send('register', { simulcast : simulcast }, function(error, answer) { 
		if (error) return setState(I_CAN_START)
		setState(I_CAN_STOP)
		callback(answer.type)
	});
}

function remoteOnIceCandidate(candidate) {
	webRtcPeer.addIceCandidate(candidate, function(error) {
		if (error)
			return console.error('Error adding candidate: ' + error);
		console.log('Remote candidate ' + JSON.stringify(candidate));
	});
}

function start() {
	console.log('Starting video call ...');

	// Disable start button
	setState(I_AM_STARTING);
	showSpinner(videoFeed);

	simulcast = $('#simulcast-checkbox').is(':checked')

	console.log('Creating WebRtcPeer and generating local sdp offer ...');
	register(function(clientType) {

		var options = {
				onicecandidate : onIceCandidate,
				onaddstream : onAddStream,
				multistream: true
		}

		$('#sfu-header').append(' ' + clientType)

		if (clientType === 'presenter') {
			isPresenter = true;
			options.simulcast = simulcast
			
			getUserMedia(constraints, function(userStream) {
		        stream = userStream;
		        videoFeed.src = URL.createObjectURL(stream)
		        videoFeed.muted = true
		        webRtcPeer = new kurentoUtils2.WebRtcPeer2.WebRtcPeer2Sendonly(options, onNegotiateWebRtcPresenterCallback);   
			}, function(error) {
				setState(I_CAN_START);
		        return console.error('Access denied to webcam', error);
		    });
		} else {
			$('#add-stream').hide();
			$('#remove-stream').hide();
			$('#simulcast-label').hide();
			webRtcPeer = new kurentoUtils2.WebRtcPeer2.WebRtcPeer2Recvonly(options, onNegotiateWebRtcViewerCallback);
		}
	});
}

function onNegotiateWebRtcPresenterCallback(error) {
	
	if (error) return setState(I_CAN_START)
	
	this.peerConnection.addStream(stream);
	
	this.generateOffer(onSdpOfferPresenterCallback);
}

function onSdpOfferPresenterCallback(error, sdpOffer) {
	if (error) {
		setState(I_CAN_START);
		return console.error('Error generating the offer:', error);
	}
	
	//must be the same as 'stream'
	// NOT SUPPORTED IN FIREFOX
//	originStream = this.peerConnection.getLocalStreams()[0].clone();
	
	jsonrpcClient.send('negotiateWebRtc', {sdpOffer: sdpOffer}, function(error, answer) { 
		if (error) return setState(I_CAN_START)

		if (newStreams > 0)
			setState(STREAMS_ADDED)
		else
			setState(I_CAN_STOP)

		console.log('SDP answer received from server. Processing ...');

		webRtcPeer.processAnswer(answer.sdpAnswer, function(error) {
			if (error) {
				setState(I_CAN_START);
				return console.error('Error processing the answer:', error);
			}
		});
	});
}

function onSdpAnswerViewerCallback(error, sdpAnswer) {
	if (error) return console.error(error);

	jsonrpcClient.send('processAnswer', { sdpAnswer : sdpAnswer}, function(error) {
		if (error) return setState(I_CAN_START)
	});
}

function onNegotiateWebRtcViewerCallback(error) {
	
	if (error) return setState(I_CAN_START)
	
	jsonrpcClient.send('negotiateWebRtc', {}, function(error, answer) {
		if (error) return setState(I_CAN_START)

		setState(I_CAN_STOP)

		console.log('SDP offer received from server. Processing ...');

		webRtcPeer.processOffer(answer.sdpOffer, onSdpAnswerViewerCallback);
	});
}

function viewerNegotiation(remoteSdpOffer) {
	webRtcPeer.processOffer(remoteSdpOffer, onSdpAnswerViewerCallback);
}

function onAddStream(event) {
	console.info('onAddStream: ', event);
	stream = event.stream

    console.info('Remote stream #' + stream.id + ': audioTracks=' +
    		stream.getAudioTracks().length + ' videoTracks=' +
    		stream.getVideoTracks().length)

	videoFeed.pause()
    videoFeed.src = URL.createObjectURL(stream);
	videoFeed.load()
}

function onIceCandidate(candidate) {
	console.log('Local candidate' + JSON.stringify(candidate));

	jsonrpcClient.send('iceCandidate', {
		candidate : candidate
	}, function(error, answer) { 
		if (error) return console.error(error)
	});
}

function stop() {
	if (webRtcPeer) {
		console.log('Stopping video call ...');
		setState(I_CAN_START);
		webRtcPeer.dispose();
		webRtcPeer = null;

		jsonrpcClient.send('stop', {}, function(error) { 
			if (error) return console.error(error)
		});

		hideSpinner(videoFeed);
	}
}

function addVideoStream() {
	if (!isPresenter) return console.error('Must be presenter')
	if (!webRtcPeer) return console.error('No WebRtcPeer created')
	var pc = webRtcPeer.peerConnection;
    if (!pc || typeof pc === 'undefined') 
    	return console.error('PeerConnection not created: ', pc)
	
	console.log("Adding new video stream from gUM");
    
	setState(I_AM_STARTING);
	getUserMedia(constraints, function(userStream) {
				//replace displaying media
		        videoFeed.pause();
		        videoFeed.src = URL.createObjectURL(userStream);
		        videoFeed.load();
		        
		        pc.addStream(userStream);
		        
		        newStreams++;
		        
		        webRtcPeer.generateOffer(onSdpOfferPresenterCallback);
			}, function(error) {
				setState(I_CAN_START);
		        return console.error('Access denied to webcam', error);
		    });
}

function removeVideoStream() {
	if (!isPresenter) return console.error('Must be presenter')
	if (!webRtcPeer) return console.error('No WebRtcPeer created')
	
    var pc = webRtcPeer.peerConnection;
    if (!pc || typeof pc === 'undefined') 
    	return console.error('PeerConnection not created: ', pc)

    var totalStreams = pc.getLocalStreams().length;
	if (!(totalStreams > 1))
		return console.error('There are currently ' + totalStreams + ' streams, can only remove if > 1')
		
    console.log("Remove last video stream from a list of " + totalStreams);
    
	var lastStream = pc.getLocalStreams()[totalStreams - 1];
	pc.removeStream(lastStream);

	newStreams--;
	
    setState(I_AM_STARTING);
	webRtcPeer.generateOffer(onSdpOfferPresenterCallback);
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
		$('#remove-stream').attr('disabled', true);
		$('#remove-stream').removeAttr('onclick');

		if (webRtcPeer) webRtcPeer.dispose();
		break;

	case I_CAN_STOP:
		$('#simulcast-checkbox').attr('disabled', true);
		$('#simulcast-label').addClass('disabled');
		$('#start').attr('disabled', true);
		$('#stop').attr('disabled', false);
		$('#stop').attr('onclick', 'stop()');
		$('#add-stream').attr('disabled', false);
		$('#add-stream').attr('onclick', 'addVideoStream()');
		$('#remove-stream').attr('disabled', true);
		$('#remove-stream').removeAttr('onclick');
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
		$('#remove-stream').attr('disabled', true);
		$('#remove-stream').removeAttr('onclick');
		break;
	
	case STREAMS_ADDED:
		$('#simulcast-checkbox').attr('disabled', true);
		$('#simulcast-label').addClass('disabled');
		$('#start').attr('disabled', true);
		$('#stop').attr('disabled', false);
		$('#stop').attr('onclick', 'stop()');
		$('#add-stream').attr('disabled', false);
		$('#add-stream').attr('onclick', 'addVideoStream()');
		$('#remove-stream').attr('disabled', false);
		$('#remove-stream').attr('onclick', 'removeVideoStream()');
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
		arguments[i].pause();
		arguments[i].src = '';
		arguments[i].load();
		arguments[i].poster = './img/webrtc.png';
		arguments[i].style.background = '';
	}
}

function setVideoFakeFF(){
	constraints.fake = true;
}

/**
 * Lightbox utility (to display media pipeline image in a modal dialog)
 */
$(document).delegate('*[data-toggle="lightbox"]', 'click', function(event) {
	event.preventDefault();
	$(this).ekkoLightbox();
});
