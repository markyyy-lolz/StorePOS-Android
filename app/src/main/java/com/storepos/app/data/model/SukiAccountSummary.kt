package com.storepos.app.data.model

/**
 * Read-only customer account presentation. Customer store credit (prepaid value)
 * and unpaid receivables (utang) must NEVER be mixed into one balance.
 */
object SukiAccountSummary {
    fun membershipReference(customer: Customer): String =
        "SUKI-" + customer.id.replace("-", "").take(12).uppercase(java.util.Locale.US)

    fun outstanding(customer: Customer, receivables: List<CustomerReceivable>): Double =
        receivables.asSequence()
            .filter { it.shopId == customer.shopId && it.customerId == customer.id }
            .sumOf { it.balance.coerceAtLeast(0.0) }

    fun availableCredit(customer: Customer, receivables: List<CustomerReceivable>): Double =
        (customer.creditLimit.coerceAtLeast(0.0) - outstanding(customer, receivables))
            .coerceAtLeast(0.0)
}
