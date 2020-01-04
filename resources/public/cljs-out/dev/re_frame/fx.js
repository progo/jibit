// Compiled by ClojureScript 1.10.520 {}
goog.provide('re_frame.fx');
goog.require('cljs.core');
goog.require('re_frame.router');
goog.require('re_frame.db');
goog.require('re_frame.interceptor');
goog.require('re_frame.interop');
goog.require('re_frame.events');
goog.require('re_frame.registrar');
goog.require('re_frame.loggers');
goog.require('re_frame.trace');
re_frame.fx.kind = new cljs.core.Keyword(null,"fx","fx",-1237829572);
if(cljs.core.truth_(re_frame.registrar.kinds.call(null,re_frame.fx.kind))){
} else {
throw (new Error("Assert failed: (re-frame.registrar/kinds kind)"));
}
/**
 * Register the given effect `handler` for the given `id`.
 * 
 *   `id` is keyword, often namespaced.
 *   `handler` is a side-effecting function which takes a single argument and whose return
 *   value is ignored.
 * 
 *   Example Use
 *   -----------
 * 
 *   First, registration ... associate `:effect2` with a handler.
 * 
 *   (reg-fx
 *   :effect2
 *   (fn [value]
 *      ... do something side-effect-y))
 * 
 *   Then, later, if an event handler were to return this effects map ...
 * 
 *   {...
 * :effect2  [1 2]}
 * 
 * ... then the `handler` `fn` we registered previously, using `reg-fx`, will be
 * called with an argument of `[1 2]`.
 */
re_frame.fx.reg_fx = (function re_frame$fx$reg_fx(id,handler){
return re_frame.registrar.register_handler.call(null,re_frame.fx.kind,id,handler);
});
/**
 * An interceptor whose `:after` actions the contents of `:effects`. As a result,
 *   this interceptor is Domino 3.
 * 
 *   This interceptor is silently added (by reg-event-db etc) to the front of
 *   interceptor chains for all events.
 * 
 *   For each key in `:effects` (a map), it calls the registered `effects handler`
 *   (see `reg-fx` for registration of effect handlers).
 * 
 *   So, if `:effects` was:
 *    {:dispatch  [:hello 42]
 *     :db        {...}
 *     :undo      "set flag"}
 * 
 *   it will call the registered effect handlers for each of the map's keys:
 *   `:dispatch`, `:undo` and `:db`. When calling each handler, provides the map
 *   value for that key - so in the example above the effect handler for :dispatch
 *   will be given one arg `[:hello 42]`.
 * 
 *   You cannot rely on the ordering in which effects are executed.
 */
re_frame.fx.do_fx = re_frame.interceptor.__GT_interceptor.call(null,new cljs.core.Keyword(null,"id","id",-1388402092),new cljs.core.Keyword(null,"do-fx","do-fx",1194163050),new cljs.core.Keyword(null,"after","after",594996914),(function re_frame$fx$do_fx_after(context){
if(re_frame.trace.is_trace_enabled_QMARK_.call(null)){
var _STAR_current_trace_STAR__orig_val__20430 = re_frame.trace._STAR_current_trace_STAR_;
var _STAR_current_trace_STAR__temp_val__20431 = re_frame.trace.start_trace.call(null,new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"op-type","op-type",-1636141668),new cljs.core.Keyword("event","do-fx","event/do-fx",1357330452)], null));
re_frame.trace._STAR_current_trace_STAR_ = _STAR_current_trace_STAR__temp_val__20431;

try{try{var seq__20432 = cljs.core.seq.call(null,new cljs.core.Keyword(null,"effects","effects",-282369292).cljs$core$IFn$_invoke$arity$1(context));
var chunk__20433 = null;
var count__20434 = (0);
var i__20435 = (0);
while(true){
if((i__20435 < count__20434)){
var vec__20442 = cljs.core._nth.call(null,chunk__20433,i__20435);
var effect_key = cljs.core.nth.call(null,vec__20442,(0),null);
var effect_value = cljs.core.nth.call(null,vec__20442,(1),null);
var temp__5455__auto___20464 = re_frame.registrar.get_handler.call(null,re_frame.fx.kind,effect_key,false);
if(cljs.core.truth_(temp__5455__auto___20464)){
var effect_fn_20465 = temp__5455__auto___20464;
effect_fn_20465.call(null,effect_value);
} else {
re_frame.loggers.console.call(null,new cljs.core.Keyword(null,"error","error",-978969032),"re-frame: no handler registered for effect:",effect_key,". Ignoring.");
}


var G__20466 = seq__20432;
var G__20467 = chunk__20433;
var G__20468 = count__20434;
var G__20469 = (i__20435 + (1));
seq__20432 = G__20466;
chunk__20433 = G__20467;
count__20434 = G__20468;
i__20435 = G__20469;
continue;
} else {
var temp__5457__auto__ = cljs.core.seq.call(null,seq__20432);
if(temp__5457__auto__){
var seq__20432__$1 = temp__5457__auto__;
if(cljs.core.chunked_seq_QMARK_.call(null,seq__20432__$1)){
var c__4550__auto__ = cljs.core.chunk_first.call(null,seq__20432__$1);
var G__20470 = cljs.core.chunk_rest.call(null,seq__20432__$1);
var G__20471 = c__4550__auto__;
var G__20472 = cljs.core.count.call(null,c__4550__auto__);
var G__20473 = (0);
seq__20432 = G__20470;
chunk__20433 = G__20471;
count__20434 = G__20472;
i__20435 = G__20473;
continue;
} else {
var vec__20445 = cljs.core.first.call(null,seq__20432__$1);
var effect_key = cljs.core.nth.call(null,vec__20445,(0),null);
var effect_value = cljs.core.nth.call(null,vec__20445,(1),null);
var temp__5455__auto___20474 = re_frame.registrar.get_handler.call(null,re_frame.fx.kind,effect_key,false);
if(cljs.core.truth_(temp__5455__auto___20474)){
var effect_fn_20475 = temp__5455__auto___20474;
effect_fn_20475.call(null,effect_value);
} else {
re_frame.loggers.console.call(null,new cljs.core.Keyword(null,"error","error",-978969032),"re-frame: no handler registered for effect:",effect_key,". Ignoring.");
}


var G__20476 = cljs.core.next.call(null,seq__20432__$1);
var G__20477 = null;
var G__20478 = (0);
var G__20479 = (0);
seq__20432 = G__20476;
chunk__20433 = G__20477;
count__20434 = G__20478;
i__20435 = G__20479;
continue;
}
} else {
return null;
}
}
break;
}
}finally {if(re_frame.trace.is_trace_enabled_QMARK_.call(null)){
var end__20252__auto___20480 = re_frame.interop.now.call(null);
var duration__20253__auto___20481 = (end__20252__auto___20480 - new cljs.core.Keyword(null,"start","start",-355208981).cljs$core$IFn$_invoke$arity$1(re_frame.trace._STAR_current_trace_STAR_));
cljs.core.swap_BANG_.call(null,re_frame.trace.traces,cljs.core.conj,cljs.core.assoc.call(null,re_frame.trace._STAR_current_trace_STAR_,new cljs.core.Keyword(null,"duration","duration",1444101068),duration__20253__auto___20481,new cljs.core.Keyword(null,"end","end",-268185958),re_frame.interop.now.call(null)));

re_frame.trace.run_tracing_callbacks_BANG_.call(null,end__20252__auto___20480);
} else {
}
}}finally {re_frame.trace._STAR_current_trace_STAR_ = _STAR_current_trace_STAR__orig_val__20430;
}} else {
var seq__20448 = cljs.core.seq.call(null,new cljs.core.Keyword(null,"effects","effects",-282369292).cljs$core$IFn$_invoke$arity$1(context));
var chunk__20449 = null;
var count__20450 = (0);
var i__20451 = (0);
while(true){
if((i__20451 < count__20450)){
var vec__20458 = cljs.core._nth.call(null,chunk__20449,i__20451);
var effect_key = cljs.core.nth.call(null,vec__20458,(0),null);
var effect_value = cljs.core.nth.call(null,vec__20458,(1),null);
var temp__5455__auto___20482 = re_frame.registrar.get_handler.call(null,re_frame.fx.kind,effect_key,false);
if(cljs.core.truth_(temp__5455__auto___20482)){
var effect_fn_20483 = temp__5455__auto___20482;
effect_fn_20483.call(null,effect_value);
} else {
re_frame.loggers.console.call(null,new cljs.core.Keyword(null,"error","error",-978969032),"re-frame: no handler registered for effect:",effect_key,". Ignoring.");
}


var G__20484 = seq__20448;
var G__20485 = chunk__20449;
var G__20486 = count__20450;
var G__20487 = (i__20451 + (1));
seq__20448 = G__20484;
chunk__20449 = G__20485;
count__20450 = G__20486;
i__20451 = G__20487;
continue;
} else {
var temp__5457__auto__ = cljs.core.seq.call(null,seq__20448);
if(temp__5457__auto__){
var seq__20448__$1 = temp__5457__auto__;
if(cljs.core.chunked_seq_QMARK_.call(null,seq__20448__$1)){
var c__4550__auto__ = cljs.core.chunk_first.call(null,seq__20448__$1);
var G__20488 = cljs.core.chunk_rest.call(null,seq__20448__$1);
var G__20489 = c__4550__auto__;
var G__20490 = cljs.core.count.call(null,c__4550__auto__);
var G__20491 = (0);
seq__20448 = G__20488;
chunk__20449 = G__20489;
count__20450 = G__20490;
i__20451 = G__20491;
continue;
} else {
var vec__20461 = cljs.core.first.call(null,seq__20448__$1);
var effect_key = cljs.core.nth.call(null,vec__20461,(0),null);
var effect_value = cljs.core.nth.call(null,vec__20461,(1),null);
var temp__5455__auto___20492 = re_frame.registrar.get_handler.call(null,re_frame.fx.kind,effect_key,false);
if(cljs.core.truth_(temp__5455__auto___20492)){
var effect_fn_20493 = temp__5455__auto___20492;
effect_fn_20493.call(null,effect_value);
} else {
re_frame.loggers.console.call(null,new cljs.core.Keyword(null,"error","error",-978969032),"re-frame: no handler registered for effect:",effect_key,". Ignoring.");
}


var G__20494 = cljs.core.next.call(null,seq__20448__$1);
var G__20495 = null;
var G__20496 = (0);
var G__20497 = (0);
seq__20448 = G__20494;
chunk__20449 = G__20495;
count__20450 = G__20496;
i__20451 = G__20497;
continue;
}
} else {
return null;
}
}
break;
}
}
}));
re_frame.fx.reg_fx.call(null,new cljs.core.Keyword(null,"dispatch-later","dispatch-later",291951390),(function (value){
var seq__20498 = cljs.core.seq.call(null,cljs.core.remove.call(null,cljs.core.nil_QMARK_,value));
var chunk__20499 = null;
var count__20500 = (0);
var i__20501 = (0);
while(true){
if((i__20501 < count__20500)){
var map__20506 = cljs.core._nth.call(null,chunk__20499,i__20501);
var map__20506__$1 = (((((!((map__20506 == null))))?(((((map__20506.cljs$lang$protocol_mask$partition0$ & (64))) || ((cljs.core.PROTOCOL_SENTINEL === map__20506.cljs$core$ISeq$))))?true:false):false))?cljs.core.apply.call(null,cljs.core.hash_map,map__20506):map__20506);
var effect = map__20506__$1;
var ms = cljs.core.get.call(null,map__20506__$1,new cljs.core.Keyword(null,"ms","ms",-1152709733));
var dispatch = cljs.core.get.call(null,map__20506__$1,new cljs.core.Keyword(null,"dispatch","dispatch",1319337009));
if(((cljs.core.empty_QMARK_.call(null,dispatch)) || ((!(typeof ms === 'number'))))){
re_frame.loggers.console.call(null,new cljs.core.Keyword(null,"error","error",-978969032),"re-frame: ignoring bad :dispatch-later value:",effect);
} else {
re_frame.interop.set_timeout_BANG_.call(null,((function (seq__20498,chunk__20499,count__20500,i__20501,map__20506,map__20506__$1,effect,ms,dispatch){
return (function (){
return re_frame.router.dispatch.call(null,dispatch);
});})(seq__20498,chunk__20499,count__20500,i__20501,map__20506,map__20506__$1,effect,ms,dispatch))
,ms);
}


var G__20510 = seq__20498;
var G__20511 = chunk__20499;
var G__20512 = count__20500;
var G__20513 = (i__20501 + (1));
seq__20498 = G__20510;
chunk__20499 = G__20511;
count__20500 = G__20512;
i__20501 = G__20513;
continue;
} else {
var temp__5457__auto__ = cljs.core.seq.call(null,seq__20498);
if(temp__5457__auto__){
var seq__20498__$1 = temp__5457__auto__;
if(cljs.core.chunked_seq_QMARK_.call(null,seq__20498__$1)){
var c__4550__auto__ = cljs.core.chunk_first.call(null,seq__20498__$1);
var G__20514 = cljs.core.chunk_rest.call(null,seq__20498__$1);
var G__20515 = c__4550__auto__;
var G__20516 = cljs.core.count.call(null,c__4550__auto__);
var G__20517 = (0);
seq__20498 = G__20514;
chunk__20499 = G__20515;
count__20500 = G__20516;
i__20501 = G__20517;
continue;
} else {
var map__20508 = cljs.core.first.call(null,seq__20498__$1);
var map__20508__$1 = (((((!((map__20508 == null))))?(((((map__20508.cljs$lang$protocol_mask$partition0$ & (64))) || ((cljs.core.PROTOCOL_SENTINEL === map__20508.cljs$core$ISeq$))))?true:false):false))?cljs.core.apply.call(null,cljs.core.hash_map,map__20508):map__20508);
var effect = map__20508__$1;
var ms = cljs.core.get.call(null,map__20508__$1,new cljs.core.Keyword(null,"ms","ms",-1152709733));
var dispatch = cljs.core.get.call(null,map__20508__$1,new cljs.core.Keyword(null,"dispatch","dispatch",1319337009));
if(((cljs.core.empty_QMARK_.call(null,dispatch)) || ((!(typeof ms === 'number'))))){
re_frame.loggers.console.call(null,new cljs.core.Keyword(null,"error","error",-978969032),"re-frame: ignoring bad :dispatch-later value:",effect);
} else {
re_frame.interop.set_timeout_BANG_.call(null,((function (seq__20498,chunk__20499,count__20500,i__20501,map__20508,map__20508__$1,effect,ms,dispatch,seq__20498__$1,temp__5457__auto__){
return (function (){
return re_frame.router.dispatch.call(null,dispatch);
});})(seq__20498,chunk__20499,count__20500,i__20501,map__20508,map__20508__$1,effect,ms,dispatch,seq__20498__$1,temp__5457__auto__))
,ms);
}


var G__20518 = cljs.core.next.call(null,seq__20498__$1);
var G__20519 = null;
var G__20520 = (0);
var G__20521 = (0);
seq__20498 = G__20518;
chunk__20499 = G__20519;
count__20500 = G__20520;
i__20501 = G__20521;
continue;
}
} else {
return null;
}
}
break;
}
}));
re_frame.fx.reg_fx.call(null,new cljs.core.Keyword(null,"dispatch","dispatch",1319337009),(function (value){
if((!(cljs.core.vector_QMARK_.call(null,value)))){
return re_frame.loggers.console.call(null,new cljs.core.Keyword(null,"error","error",-978969032),"re-frame: ignoring bad :dispatch value. Expected a vector, but got:",value);
} else {
return re_frame.router.dispatch.call(null,value);
}
}));
re_frame.fx.reg_fx.call(null,new cljs.core.Keyword(null,"dispatch-n","dispatch-n",-504469236),(function (value){
if((!(cljs.core.sequential_QMARK_.call(null,value)))){
return re_frame.loggers.console.call(null,new cljs.core.Keyword(null,"error","error",-978969032),"re-frame: ignoring bad :dispatch-n value. Expected a collection, but got:",value);
} else {
var seq__20522 = cljs.core.seq.call(null,cljs.core.remove.call(null,cljs.core.nil_QMARK_,value));
var chunk__20523 = null;
var count__20524 = (0);
var i__20525 = (0);
while(true){
if((i__20525 < count__20524)){
var event = cljs.core._nth.call(null,chunk__20523,i__20525);
re_frame.router.dispatch.call(null,event);


var G__20526 = seq__20522;
var G__20527 = chunk__20523;
var G__20528 = count__20524;
var G__20529 = (i__20525 + (1));
seq__20522 = G__20526;
chunk__20523 = G__20527;
count__20524 = G__20528;
i__20525 = G__20529;
continue;
} else {
var temp__5457__auto__ = cljs.core.seq.call(null,seq__20522);
if(temp__5457__auto__){
var seq__20522__$1 = temp__5457__auto__;
if(cljs.core.chunked_seq_QMARK_.call(null,seq__20522__$1)){
var c__4550__auto__ = cljs.core.chunk_first.call(null,seq__20522__$1);
var G__20530 = cljs.core.chunk_rest.call(null,seq__20522__$1);
var G__20531 = c__4550__auto__;
var G__20532 = cljs.core.count.call(null,c__4550__auto__);
var G__20533 = (0);
seq__20522 = G__20530;
chunk__20523 = G__20531;
count__20524 = G__20532;
i__20525 = G__20533;
continue;
} else {
var event = cljs.core.first.call(null,seq__20522__$1);
re_frame.router.dispatch.call(null,event);


var G__20534 = cljs.core.next.call(null,seq__20522__$1);
var G__20535 = null;
var G__20536 = (0);
var G__20537 = (0);
seq__20522 = G__20534;
chunk__20523 = G__20535;
count__20524 = G__20536;
i__20525 = G__20537;
continue;
}
} else {
return null;
}
}
break;
}
}
}));
re_frame.fx.reg_fx.call(null,new cljs.core.Keyword(null,"deregister-event-handler","deregister-event-handler",-1096518994),(function (value){
var clear_event = cljs.core.partial.call(null,re_frame.registrar.clear_handlers,re_frame.events.kind);
if(cljs.core.sequential_QMARK_.call(null,value)){
var seq__20538 = cljs.core.seq.call(null,value);
var chunk__20539 = null;
var count__20540 = (0);
var i__20541 = (0);
while(true){
if((i__20541 < count__20540)){
var event = cljs.core._nth.call(null,chunk__20539,i__20541);
clear_event.call(null,event);


var G__20542 = seq__20538;
var G__20543 = chunk__20539;
var G__20544 = count__20540;
var G__20545 = (i__20541 + (1));
seq__20538 = G__20542;
chunk__20539 = G__20543;
count__20540 = G__20544;
i__20541 = G__20545;
continue;
} else {
var temp__5457__auto__ = cljs.core.seq.call(null,seq__20538);
if(temp__5457__auto__){
var seq__20538__$1 = temp__5457__auto__;
if(cljs.core.chunked_seq_QMARK_.call(null,seq__20538__$1)){
var c__4550__auto__ = cljs.core.chunk_first.call(null,seq__20538__$1);
var G__20546 = cljs.core.chunk_rest.call(null,seq__20538__$1);
var G__20547 = c__4550__auto__;
var G__20548 = cljs.core.count.call(null,c__4550__auto__);
var G__20549 = (0);
seq__20538 = G__20546;
chunk__20539 = G__20547;
count__20540 = G__20548;
i__20541 = G__20549;
continue;
} else {
var event = cljs.core.first.call(null,seq__20538__$1);
clear_event.call(null,event);


var G__20550 = cljs.core.next.call(null,seq__20538__$1);
var G__20551 = null;
var G__20552 = (0);
var G__20553 = (0);
seq__20538 = G__20550;
chunk__20539 = G__20551;
count__20540 = G__20552;
i__20541 = G__20553;
continue;
}
} else {
return null;
}
}
break;
}
} else {
return clear_event.call(null,value);
}
}));
re_frame.fx.reg_fx.call(null,new cljs.core.Keyword(null,"db","db",993250759),(function (value){
if((!((cljs.core.deref.call(null,re_frame.db.app_db) === value)))){
return cljs.core.reset_BANG_.call(null,re_frame.db.app_db,value);
} else {
return null;
}
}));

//# sourceMappingURL=fx.js.map
