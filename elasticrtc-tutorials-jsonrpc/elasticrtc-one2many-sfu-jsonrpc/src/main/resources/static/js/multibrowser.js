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

var wsUri = 'https://' + location.host + '/sfu-multibrowser';
var videoFeed;
var webRtcPeer;
var isPresenter = false;
var stream;
var videoIndex = 0
var state = null;
var simulcast;

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
	document.getElementById('videoFileName').value = document.getElementById('videoFileName').value + "." + document.getElementById("selectFormat").value
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
				mediaConstraints : constraints,
				onicecandidate : onIceCandidate,
				onaddstream : onAddStream
		}

		$('#sfu-header').append(' ' + clientType)

		isPresenter = clientType === 'presenter'

		if (isPresenter) {
			options.localVideo = videoFeed
			options.simulcast = simulcast
			webRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerSendonly(options, onNegotiateWebRtcCallback);
		} else {
			options.remoteVideo = videoFeed
			webRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options, onNegotiateWebRtcCallback);
		}
	});
}

function onNegotiateWebRtcCallback(error) {

	if (error) return setState(I_CAN_START)

	var webRtcPeer = this

	if (isPresenter) {
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

	if (streamDiv) {
		var video = streamDiv.getElementsByTagName('video')[0];
		webRtcPeer.peerConnection.removeStream(video.temp)
		document.getElementById('streams').removeChild(streamDiv)
	} else if (webRtcPeer) {

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

function startRecording() {
	var selectFormat = document.getElementById('selectFormat')
	var path = document.getElementById('videoFileName')
	jsonrpcClient.send('startRecording', {
		path : path.value,
		mediaProfile : selectFormat.value
	}, function(error) { 
		if (error) return console.error(error)
	});
}

function stopRecording() {
	jsonrpcClient.send('stopRecording', {}, function(error) { 
		if (error) return console.error(error)
	});
}

function videoFormatChange() {
	var path = document.getElementById("videoFileName").value;
	document.getElementById("videoFileName").value = path.substr(0,path.lastIndexOf(".")) + "." + document.getElementById("selectFormat").value;
}

function videoFileNameChange(videoFileName) {
	if (videoFileName != ""){
		if (videoFileName.indexOf(document.getElementById("selectFormat").value, videoFileName.length - document.getElementById("selectFormat").value.length) == -1)
			document.getElementById("videoFileName").value = videoFileName + "." + document.getElementById("selectFormat").value;
		else
			document.getElementById("videoFileName").value = videoFileName;
	}
}

function addStream() {
	var streamId = 'stream' + videoIndex
	var videoId = 'video' + videoIndex
	var newDiv = document.createElement('div')
	newDiv.id = streamId 
	newDiv.class = "row"
		newDiv.innerHTML = '	<div class="col-md-5">' +
		'		<h3>Local stream ' + videoIndex +'</h3>' +
		'		<video id="' + videoId + '" autoplay width="480px" height="360px"' +
		'			poster="img/webrtc.png"></video>' +
		'	</div>' +
		'   <div class="col-md-2">' +
		'		<a href="#" class="btn btn-danger" onclick="stop(' + streamId + ')">' +
		'      		<span class="glyphicon glyphicon-stop"></span> Stop </a>' +
		'	</div>'
		document.getElementById('streams').appendChild(newDiv)
		cloneInputStream(videoId)
		videoIndex++
}

function cloneInputStream(videoId) {
	var videoTag = document.getElementById(videoId)
	var newLocalStream = stream.clone()
	videoTag.temp = newLocalStream
	webRtcPeer.peerConnection.addStream(newLocalStream)
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
		$('#startRecording').attr('disabled', true);
		$('#startRecording').removeAttr('onclick');
		$('#stopRecording').attr('disabled', true);
		$('#stoptRecording').removeAttr('onclick');

		if (webRtcPeer) webRtcPeer.dispose();
		break;

	case I_CAN_STOP:
		$('#simulcast-checkbox').attr('disabled', true);
		$('#simulcast-label').addClass('disabled');
		$('#start').attr('disabled', true);
		$('#stop').attr('disabled', false);
		$('#stop').attr('onclick', 'stop()');
		$('#add-stream').attr('disabled', false);
		$('#add-stream').attr('onclick', 'addStream()');
		$('#startRecording').attr('disabled', false);
		$('#startRecording').attr('onclick', 'startRecording()');
		$('#stopRecording').attr('disabled', false);
		$('#stopRecording').attr('onclick', 'stopRecording()');
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
		$('#startRecording').attr('disabled', true);
		$('#startRecording').removeAttr('onclick');
		$('#stopRecording').attr('disabled', true);
		$('#stoptRecording').removeAttr('onclick');
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
