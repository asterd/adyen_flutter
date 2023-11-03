import 'dart:async';

import 'package:adyen_dropin/enums/adyen_error.dart';
import 'package:adyen_dropin/enums/adyen_response.dart';
import 'package:adyen_dropin/exceptions/adyen_exception.dart';
import 'package:flutter/services.dart';

class FlutterAdyen {
  static const MethodChannel _channel = const MethodChannel('flutter_adyen');

  static Future<AdyenResponse> openDropIn({
      paymentMethods,
      required String baseUrl,
      String authToken = '',
      required String clientKey,
      String publicKey = '',
      Map<String, String> lineItem = const {},
      String locale = 'it_IT',
      required String amount,
      String currency = 'EUR',
      required String returnUrl,
      String? reference,
      required String shopperReference,
      required String merchantAccount,
      Map<String, String> additionalData = const {},
      environment = 'TEST'
  }) async {
    Map<String, dynamic> args = {};
    args.putIfAbsent('paymentMethods', () => paymentMethods);
    args.putIfAbsent('additionalData', () => additionalData);
    args.putIfAbsent('baseUrl', () => baseUrl);
    args.putIfAbsent('clientKey', () => clientKey);
    args.putIfAbsent('publicKey', () => publicKey);
    args.putIfAbsent('amount', () => amount);
    args.putIfAbsent('locale', () => locale);
    args.putIfAbsent('currency', () => currency);
    args.putIfAbsent('lineItem', () => lineItem);
    args.putIfAbsent('returnUrl', () => returnUrl);
    args.putIfAbsent('environment', () => environment);
    args.putIfAbsent('shopperReference', () => shopperReference);
    args.putIfAbsent('authToken', () => authToken);
    args.putIfAbsent('merchantAccount', () => merchantAccount);
    args.putIfAbsent('reference', () => reference);

    // final String response = await _channel.invokeMethod('openDropIn', args);
    // return response;
    final response =  await _channel.invokeMethod<String>('openDropIn', args);
    switch(response) {
      case 'PAYMENT_ERROR':
        throw AdyenException(AdyenError.PAYMENT_ERROR, response);
      case 'PAYMENT_CANCELLED':
        throw AdyenException(AdyenError.PAYMENT_CANCELLED, response);
    }

    return AdyenResponse.values.firstWhere((element) =>
        element.name.toLowerCase() == response?.toLowerCase(),
        orElse: () => throw AdyenException(AdyenError.PAYMENT_ERROR, response));
  }
}
