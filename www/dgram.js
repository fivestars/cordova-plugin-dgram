var exec = cordova.require('cordova/exec');

function Socket(port, isBroadcast, success, error) {
    this._eventHandlers = { };
    Socket.socket = this;
    console.log("about to send exec command");
    exec(success, error, 'Dgram', 'open', [ port, isBroadcast ? 1 : 0 ]);
    console.log("sent socket exec command");
}

Socket.socketCount = 0;
Socket.socket = { };

Socket.prototype.on = function (eventType, callback) {
    this._eventHandlers[eventType] = callback;
};

Socket.prototype.listen = function (success, error) {
    console.log("attempting to listen");
    exec(success, error, 'Dgram', 'listen');
};

Socket.prototype.close = function (success, error) {
    exec(success, error, 'Dgram', 'close', [ this._socketId ]);
    delete Socket.sockets[this._socketId];
    this._socketId = 0;
};

Socket.prototype.send = function (msg, destAddress, destPort, error) {
    exec(null, error, 'Dgram', 'send', [ msg, destAddress, destPort ]);
};

Socket.prototype.address = function () {
};

function createSocket(port, isBroadcast, success, error) {
    iport = parseInt(port, 10);
    if (isNaN(iport) || iport === 0){
        throw new Error('Illegal Port number');
    }
    console.log("attempting to open socket");
    return new Socket(iport, isBroadcast, success, error);
}

function onMessage(id, message, remoteAddress, remotePort) {
    console.log("calling onMessage");
    var socket = Socket.socket;
    if (socket && 'message' in socket._eventHandlers) {
        console.log("set event handlers");
        socket._eventHandlers['message'].call(null, message, { address: remoteAddress, port: remotePort });
    }
}

function onSend(id) {
    if (Socket.socket && 'send' in Socket.socket._eventHandlers) {
        Socket.socket._eventHandlers['send']();
    }
}

function onError(id, action, errorMessage) {
    var socket = Socket.socket;
    if (socket && 'error' in socket._eventHandlers) {
        socket._eventHandlers['error'].call(null, action, errorMessage);
    }
}

module.exports = {
    createSocket: createSocket,
    // These handlers are used by Cordova as callbacks,
    // but should not be used directly from JS
    _onMessage: onMessage,
    _onSend: onSend,
    _onError: onError
};
});
