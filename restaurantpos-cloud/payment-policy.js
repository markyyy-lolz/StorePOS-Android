/**
 * Cash may not be recorded against a pending PayMongo checkout.
 * This prevents two simultaneous settlement paths for one restaurant order.
 */
export function cashSettlementDecision(orderStatus, gatewayStatus){
 if(orderStatus==='paid')return 'already_paid';
 if(orderStatus!=='open')return 'not_open';
 if(['pending','paid','conflict'].includes(gatewayStatus))return 'gateway_review';
 return 'allowed';
}
