package xdman.network

import java.io.IOException

class HostUnreachableException : IOException {
    constructor() : super()
    constructor(msg: String) : super(msg)
}
