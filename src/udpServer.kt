import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress

/**
 * Created by Ahmad Al-Sharif on 07/09 - Sep/17.
 */

data class Command(val value: String, val data: List<String>, val senderAddress: String, val senderPort: Int) {

    fun fromString(s: String, address: String, port: Int): Command {
        val data = s.split(":")
        return Command(data[0], data.subList(1, data.size), address, port)
    }

    companion object {
        const val REGISTER = "REGISTER"
        const val CONNECT = "CONNECT"
        const val ECHO = "ECHO"
    }
}

data class Connection(
        val privateAddress: String,
        val privatePort: Int,
        val publicAddress: String,
        val publicPort: Int,
        val name: String
) {
    val inetPrivateAddress: InetAddress by lazy { Inet4Address.getByName(privateAddress) }
    val inetPublicAddress: InetAddress by lazy { Inet4Address.getByName(publicAddress) }

    fun send(socket: DatagramSocket, message: String) {
        val sendPacket = DatagramPacket(message.toByteArray(), message.length, inetPrivateAddress, privatePort)
        socket.send(sendPacket)
        sendPacket.address = inetPublicAddress
        sendPacket.port = publicPort
        socket.send(sendPacket)
    }
}


fun main(args: Array<String>) {
    val connections = mutableListOf<Connection>()
    val commandArrived = BehaviorSubject.create<Command>()
    val serverSocket = DatagramSocket(9876)
    val receiveData = ByteArray(1024)

    setupCommandHandlers(connections, commandArrived, serverSocket)

    while (true) {
        val receivePacket = DatagramPacket(receiveData, receiveData.size)
        serverSocket.receive(receivePacket)
        val sentence = String(receivePacket.data)
        receiveData.fill(0)
        val ipAddress = receivePacket.address
        val port = receivePacket.port
        println("RECEIVED from $ipAddress:$port")
        println(sentence)
        val data = sentence.trimEnd(0.toChar()).split(":")
        val data1 = data.subList(1, data.size)
        commandArrived.onNext(Command(data[0], data1, ipAddress.hostAddress, port))
//        sentence.toUpperCase().toByteArray().forEachIndexed { i, c -> sendData[i] = c }
//        val sendPacket = DatagramPacket(sendData, sendData.size, ipAddress, port)
//        serverSocket.send(sendPacket)
//        println("SENT to $ipAddress:$port => ${String(sendData)}")
//        sendData.fill(0)
    }
}

fun setupCommandHandlers(
        connections: MutableList<Connection>,
        commandArrived: Observable<Command>,
        serverSocket: DatagramSocket
) {
    commandArrived
//            .subscribeOn(Schedulers.single())
            .filter { it.value == Command.REGISTER }
            .subscribe { cmd ->
                try {
                    val newConnection = Connection(
                            cmd.data[0],
                            cmd.data[1].toInt(),
                            cmd.senderAddress,
                            cmd.senderPort,
                            cmd.data[2]
                    )
                    connections.add(newConnection)
                    println("Registered new Connection = $newConnection")
                } catch (e: ArrayIndexOutOfBoundsException) {
                    System.err.println("Bad Register request")
                    e.printStackTrace()
                }
            }

    commandArrived
            .filter { it.value == Command.ECHO }
            .subscribe { cmd ->
                connections.firstOrNull { it.name == cmd.data[0] }?.send(serverSocket, cmd.toString())
            }

    commandArrived
            .filter { it.value == Command.CONNECT }
            .subscribe { cmd ->
                connections
                        .filter { it.name == cmd.data[0] || it.name == cmd.data[1] }
                        .let { peers ->
                            if (peers.size < 2) return@let
                            val (p1, p2) = peers
                            p1.send(serverSocket, p2.toString())
                            p2.send(serverSocket, p1.toString())
                        }
            }
}