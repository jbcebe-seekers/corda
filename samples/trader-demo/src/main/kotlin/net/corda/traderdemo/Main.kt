package net.corda.traderdemo

import net.corda.bank.flow.IssuerFlow
import net.corda.core.node.services.ServiceInfo
import net.corda.node.driver.driver
import net.corda.node.services.User
import net.corda.node.services.startFlowPermission
import net.corda.node.services.transactions.SimpleNotaryService

/**
 * This file is exclusively for being able to run your nodes through an IDE (as opposed to running deployNodes)
 * Do not use in a production environment.
 */
fun main(args: Array<String>) {
    driver(dsl = {
        val user = User("user1", "test", permissions = setOf(startFlowPermission<IssuerFlow.IssuanceRequester>()))
        startNode("Notary", setOf(ServiceInfo(SimpleNotaryService.type)))
        startNode("Bank A")
        startNode("Bank B")
        startNode("BankOfCorda", rpcUsers = listOf(user))
        waitForAllNodesToFinish()
    }, isDebug = true)
}
