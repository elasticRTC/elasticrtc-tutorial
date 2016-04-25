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

var wsUri = 'https://' + location.host + '/one2one'
var jsonrpcClient
var videoInput
var videoOutput
var webRtcPeer

var registerName;
var registerState;
const NOT_REGISTERED = 0;
const REGISTERING = 1;
const REGISTERED = 2;

var callState;
const NO_CALL = 0;
const PROCESSING_CALL = 1;
const IN_CALL = 2;
const ACCEPTED = 'ACCEPTED'
const REJECTED = 'REJECTED'



window.onload = function() {
//	console = new Console()
	setRegisterState(NOT_REGISTERED)
	var drag = new Draggabilly(document.getElementById('videoSmall'))
	videoInput = document.getElementById('videoInput')
	videoOutput = document.getElementById('videoOutput')
	document.getElementById('name').focus()

	var config = {
		sendCloseMessage : true,
		ws : {
			uri : wsUri,
			useSockJS : true
		},
		rpc : {
			requestTimeout : 15000,
			iceCandidate : remoteIceCandidate,
			incomingCall : onIncomingCall
		}
	};

	jsonrpcClient = new RpcBuilder.clients.JsonRpcClient(config)
}

window.onbeforeunload = function() {
	console.info('Invoking onbeforeunload')
	stop()
	jsonrpcClient.close()
}

function register() {
	var name = document.getElementById('name').value;
	if (name === '') {
		window.alert('You must provide a name');
		return;
	}
	setRegisterState(REGISTERING);

	jsonrpcClient.send('register', { name : name}, function(error, answer) { 
		if (error) return setRegisterState(NOT_REGISTERED)

		if (answer.value === 'accepted') {
			setRegisterState(REGISTERED)
		} else {
			setRegisterState(NOT_REGISTERED)
			var errorMessage = answer.value ? answer.value
					: 'Unknown reason for register rejection.'
						console.log(errorMessage)
						alert('Error registering user. See console for further information.')
		}
	});
	document.getElementById('peer').focus();
}


function remoteIceCandidate(candidate) {
	webRtcPeer.addIceCandidate(candidate, function(error) {
		if (error)
			return console.error('Error adding candidate: ' + error);
	});
}

function call() {
	var callee = document.getElementById('peer').value
	if (callee === '') return window.alert('You must specify the client name')

	setCallState(PROCESSING_CALL);
	showSpinner(videoInput, videoOutput);

	jsonrpcClient.send('call', { to : callee }, function(error, message) { 
		if (message.response != ACCEPTED) {
			console.info('Call not accepted by peer. Closing call')
			var errorMessage = message.message ? message.message
					: 'Unknown reason for call rejection.'
						console.log(errorMessage)
						stop()
		} else {
			setCallState(IN_CALL)
			negotiateWebRtcWithKurento()
		}
	});
}

function negotiateWebRtcWithKurento() {

	var options = {
			onicecandidate : onIceCandidate,
			localVideo : videoInput,
			remoteVideo : videoOutput,
			onerror : onError
	}

	webRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerSendrecv(options, function(error) {
		if (error) return setCallState(NO_CALL)

		webRtcPeer.generateOffer(function(error, sdpOffer) {
			if (error) return setCallState(NO_CALL)
			jsonrpcClient.send('negotiateWebRtc', { sdpOffer : sdpOffer}, function(error, answer) { 
				if (error) return setCallState(NO_CALL)
				webRtcPeer.processAnswer(answer.value, function(error) {
					if (error) return console.error(error)
					setCallState(IN_CALL)
				})
			})
		})
	})
}

function onIceCandidate(candidate) {
	console.log('Local candidate' + JSON.stringify(candidate))

	jsonrpcClient.send('iceCandidate', {
		candidate : candidate
	}, function(error, answer) { 
		if (error) return console.error(error)
	});
}

function stop(message) {
	console.info('Stopping session')
	setCallState(NO_CALL)
	if (webRtcPeer) {
		webRtcPeer.dispose()
		webRtcPeer = null

		jsonrpcClient.send('stop', {}, function(error) { 
			if (error) return console.error(error)
		});
	}
	hideSpinner(videoInput, videoOutput)
}

function onError() {
	setCallState(NO_CALL)
}

function onIncomingCall(message, request) {
	// If busy just reject without disturbing user
	if (callState != NO_CALL) {
		request.reply(null, { response : REJECTED, message : 'User busy'});
	} else {
		setCallState(PROCESSING_CALL);
		if (confirm('Incoming call from ' + message.caller + '. Accept?')) {
			showSpinner(videoInput, videoOutput)

			request.reply(null, { response : ACCEPTED, message : 'User accepts call'});
			negotiateWebRtcWithKurento()

		} else {
			request.reply(null, { response : REJECTED, message : 'Declined by user'});
			stop();
		}
	}
}

function showSpinner() {
	for (var i = 0; i < arguments.length; i++) {
		arguments[i].poster = './img/transparent-1px.png';
		arguments[i].style.background = 'center transparent url("./img/spinner.gif") no-repeat';
	}
}

function hideSpinner() {
	for (var i = 0; i < arguments.length; i++) {
		arguments[i].src = '';
		arguments[i].poster = './img/webrtc.png';
		arguments[i].style.background = '';
	}
}

function disableButton(id) {
	$(id).attr('disabled', true);
	$(id).removeAttr('onclick');
}

function enableButton(id, functionName) {
	$(id).attr('disabled', false);
	$(id).attr('onclick', functionName);
}

function setRegisterState(nextState) {
	switch (nextState) {
	case NOT_REGISTERED:
		enableButton('#register', 'register()');
		setCallState(NO_CALL);
		break;
	case REGISTERING:
		disableButton('#register');
		break;
	case REGISTERED:
		disableButton('#register');
		setCallState(NO_CALL);
		break;
	default:
		return;
	}
	registerState = nextState;
}

function setCallState(nextState) {
	switch (nextState) {
	case NO_CALL:
		enableButton('#call', 'call()');
		disableButton('#terminate');
		disableButton('#play');
		break;
	case PROCESSING_CALL:
		disableButton('#call');
		disableButton('#terminate');
		disableButton('#play');
		break;
	case IN_CALL:
		disableButton('#call');
		enableButton('#terminate', 'stop()');
		disableButton('#play');
		break;
	default:
		return;
	}
	callState = nextState;
}


/**
 * Lightbox utility (to display media pipeline image in a modal dialog)
 */
$(document).delegate('*[data-toggle="lightbox"]', 'click', function(event) {
	event.preventDefault();
	$(this).ekkoLightbox();
});
