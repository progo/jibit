// Compiled by ClojureScript 1.10.520 {}
goog.provide('re_frame.trace');
goog.require('cljs.core');
goog.require('re_frame.interop');
goog.require('re_frame.loggers');
goog.require('goog.functions');
re_frame.trace.id = cljs.core.atom.call(null,(0));
re_frame.trace._STAR_current_trace_STAR_ = null;
re_frame.trace.reset_tracing_BANG_ = (function re_frame$trace$reset_tracing_BANG_(){
return cljs.core.reset_BANG_.call(null,re_frame.trace.id,(0));
});

/** @define {boolean} */
goog.define("re_frame.trace.trace_enabled_QMARK_",false);
/**
 * See https://groups.google.com/d/msg/clojurescript/jk43kmYiMhA/IHglVr_TPdgJ for more details
 */
re_frame.trace.is_trace_enabled_QMARK_ = (function re_frame$trace$is_trace_enabled_QMARK_(){
return re_frame.trace.trace_enabled_QMARK_;
});
re_frame.trace.trace_cbs = cljs.core.atom.call(null,cljs.core.PersistentArrayMap.EMPTY);
if((typeof re_frame !== 'undefined') && (typeof re_frame.trace !== 'undefined') && (typeof re_frame.trace.traces !== 'undefined')){
} else {
re_frame.trace.traces = cljs.core.atom.call(null,cljs.core.PersistentVector.EMPTY);
}
if((typeof re_frame !== 'undefined') && (typeof re_frame.trace !== 'undefined') && (typeof re_frame.trace.next_delivery !== 'undefined')){
} else {
re_frame.trace.next_delivery = cljs.core.atom.call(null,(0));
}
/**
 * Registers a tracing callback function which will receive a collection of one or more traces.
 *   Will replace an existing callback function if it shares the same key.
 */
re_frame.trace.register_trace_cb = (function re_frame$trace$register_trace_cb(key,f){
if(re_frame.trace.trace_enabled_QMARK_){
return cljs.core.swap_BANG_.call(null,re_frame.trace.trace_cbs,cljs.core.assoc,key,f);
} else {
return re_frame.loggers.console.call(null,new cljs.core.Keyword(null,"warn","warn",-436710552),"Tracing is not enabled. Please set {\"re_frame.trace.trace_enabled_QMARK_\" true} in :closure-defines. See: https://github.com/day8/re-frame-10x#installation.");
}
});
re_frame.trace.remove_trace_cb = (function re_frame$trace$remove_trace_cb(key){
cljs.core.swap_BANG_.call(null,re_frame.trace.trace_cbs,cljs.core.dissoc,key);

return null;
});
re_frame.trace.next_id = (function re_frame$trace$next_id(){
return cljs.core.swap_BANG_.call(null,re_frame.trace.id,cljs.core.inc);
});
re_frame.trace.start_trace = (function re_frame$trace$start_trace(p__20274){
var map__20275 = p__20274;
var map__20275__$1 = (((((!((map__20275 == null))))?(((((map__20275.cljs$lang$protocol_mask$partition0$ & (64))) || ((cljs.core.PROTOCOL_SENTINEL === map__20275.cljs$core$ISeq$))))?true:false):false))?cljs.core.apply.call(null,cljs.core.hash_map,map__20275):map__20275);
var operation = cljs.core.get.call(null,map__20275__$1,new cljs.core.Keyword(null,"operation","operation",-1267664310));
var op_type = cljs.core.get.call(null,map__20275__$1,new cljs.core.Keyword(null,"op-type","op-type",-1636141668));
var tags = cljs.core.get.call(null,map__20275__$1,new cljs.core.Keyword(null,"tags","tags",1771418977));
var child_of = cljs.core.get.call(null,map__20275__$1,new cljs.core.Keyword(null,"child-of","child-of",-903376662));
return new cljs.core.PersistentArrayMap(null, 6, [new cljs.core.Keyword(null,"id","id",-1388402092),re_frame.trace.next_id.call(null),new cljs.core.Keyword(null,"operation","operation",-1267664310),operation,new cljs.core.Keyword(null,"op-type","op-type",-1636141668),op_type,new cljs.core.Keyword(null,"tags","tags",1771418977),tags,new cljs.core.Keyword(null,"child-of","child-of",-903376662),(function (){var or__4131__auto__ = child_of;
if(cljs.core.truth_(or__4131__auto__)){
return or__4131__auto__;
} else {
return new cljs.core.Keyword(null,"id","id",-1388402092).cljs$core$IFn$_invoke$arity$1(re_frame.trace._STAR_current_trace_STAR_);
}
})(),new cljs.core.Keyword(null,"start","start",-355208981),re_frame.interop.now.call(null)], null);
});
re_frame.trace.debounce_time = (50);
re_frame.trace.debounce = (function re_frame$trace$debounce(f,interval){
return goog.functions.debounce(f,interval);
});
re_frame.trace.schedule_debounce = re_frame.trace.debounce.call(null,(function re_frame$trace$tracing_cb_debounced(){
var seq__20277_20297 = cljs.core.seq.call(null,cljs.core.deref.call(null,re_frame.trace.trace_cbs));
var chunk__20278_20298 = null;
var count__20279_20299 = (0);
var i__20280_20300 = (0);
while(true){
if((i__20280_20300 < count__20279_20299)){
var vec__20289_20301 = cljs.core._nth.call(null,chunk__20278_20298,i__20280_20300);
var k_20302 = cljs.core.nth.call(null,vec__20289_20301,(0),null);
var cb_20303 = cljs.core.nth.call(null,vec__20289_20301,(1),null);
try{cb_20303.call(null,cljs.core.deref.call(null,re_frame.trace.traces));
}catch (e20292){var e_20304 = e20292;
re_frame.loggers.console.call(null,new cljs.core.Keyword(null,"error","error",-978969032),"Error thrown from trace cb",k_20302,"while storing",cljs.core.deref.call(null,re_frame.trace.traces),e_20304);
}

var G__20305 = seq__20277_20297;
var G__20306 = chunk__20278_20298;
var G__20307 = count__20279_20299;
var G__20308 = (i__20280_20300 + (1));
seq__20277_20297 = G__20305;
chunk__20278_20298 = G__20306;
count__20279_20299 = G__20307;
i__20280_20300 = G__20308;
continue;
} else {
var temp__5457__auto___20309 = cljs.core.seq.call(null,seq__20277_20297);
if(temp__5457__auto___20309){
var seq__20277_20310__$1 = temp__5457__auto___20309;
if(cljs.core.chunked_seq_QMARK_.call(null,seq__20277_20310__$1)){
var c__4550__auto___20311 = cljs.core.chunk_first.call(null,seq__20277_20310__$1);
var G__20312 = cljs.core.chunk_rest.call(null,seq__20277_20310__$1);
var G__20313 = c__4550__auto___20311;
var G__20314 = cljs.core.count.call(null,c__4550__auto___20311);
var G__20315 = (0);
seq__20277_20297 = G__20312;
chunk__20278_20298 = G__20313;
count__20279_20299 = G__20314;
i__20280_20300 = G__20315;
continue;
} else {
var vec__20293_20316 = cljs.core.first.call(null,seq__20277_20310__$1);
var k_20317 = cljs.core.nth.call(null,vec__20293_20316,(0),null);
var cb_20318 = cljs.core.nth.call(null,vec__20293_20316,(1),null);
try{cb_20318.call(null,cljs.core.deref.call(null,re_frame.trace.traces));
}catch (e20296){var e_20319 = e20296;
re_frame.loggers.console.call(null,new cljs.core.Keyword(null,"error","error",-978969032),"Error thrown from trace cb",k_20317,"while storing",cljs.core.deref.call(null,re_frame.trace.traces),e_20319);
}

var G__20320 = cljs.core.next.call(null,seq__20277_20310__$1);
var G__20321 = null;
var G__20322 = (0);
var G__20323 = (0);
seq__20277_20297 = G__20320;
chunk__20278_20298 = G__20321;
count__20279_20299 = G__20322;
i__20280_20300 = G__20323;
continue;
}
} else {
}
}
break;
}

return cljs.core.reset_BANG_.call(null,re_frame.trace.traces,cljs.core.PersistentVector.EMPTY);
}),re_frame.trace.debounce_time);
re_frame.trace.run_tracing_callbacks_BANG_ = (function re_frame$trace$run_tracing_callbacks_BANG_(now){
if(((cljs.core.deref.call(null,re_frame.trace.next_delivery) - (25)) < now)){
re_frame.trace.schedule_debounce.call(null);

return cljs.core.reset_BANG_.call(null,re_frame.trace.next_delivery,(now + re_frame.trace.debounce_time));
} else {
return null;
}
});

//# sourceMappingURL=trace.js.map
