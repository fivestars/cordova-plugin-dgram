var exec = cordova.require('cordova/exec');

function Socket(port, isBroadcast, success, error) {
    // Each socket gets a unique id from this static value.
    // Used on both sides of the bridge to identify each connection.
    this._socketId = ++Socket.socketCount;
    this._eventHandlers = { };
    Socket.sockets[this._socketId] = this;
    exec(success, error, 'UdpPlugin', 'open', [ this._socketId, port, isBroadcast ? 1 : 0 ]);
}

Socket.socketCount = 0;
Socket.sockets = { };

Socket.prototype.on = function (eventType, callback) {
    this._eventHandlers[eventType] = callback;
};

Socket.prototype.listen = function (success, error) {
    exec(success, error, 'UdpPlugin', 'listen', [ this._socketId ]);
};

Socket.prototype.close = function (success, error) {
    exec(success, error, 'UdpPlugin', 'close', [ this._socketId ]);
    delete Socket.sockets[this._socketId];
    this._socketId = 0;
};

Socket.prototype.send = function (msg, destAddress, destPort, error) {
    exec(null, error, 'UdpPlugin', 'send', [ this._socketId, msg, destAddress, destPort ]);
};

Socket.prototype.address = function () {
};

function createSocket(port, isBroadcast, success, error) {
    iport = parseInt(port, 10);
    if (isNaN(iport) || iport === 0){
        throw new Error('Illegal Port number');
    }
    return new Socket(iport, isBroadcast, success, error);
}

function onMessage(id, message, remoteAddress, remotePort) {
    var socket = Socket.sockets[id];
    if (socket && 'message' in socket._eventHandlers) {
        socket._eventHandlers['message'].call(null, message, { address: remoteAddress, port: remotePort });
    }
}

function onSend(id) {
    var socket = Socket.sockets[id];
    if (socket && 'send' in socket._eventHandlers) {
        socket._eventHandlers['send']();
    }
}

function onError(id, action, errorMessage) {
    var socket = Socket.sockets[id];
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