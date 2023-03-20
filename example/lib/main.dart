import 'dart:convert';

import 'package:adyen_dropin/enums/adyen_response.dart';
import 'package:adyen_dropin/exceptions/adyen_exception.dart';
import 'package:adyen_dropin/flutter_adyen.dart';
import 'package:flutter/material.dart';

import 'mock_data.dart';

void main() => runApp(MyApp());

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String _payment_result = 'Unknown';

  AdyenResponse? dropInResponse;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        floatingActionButton: FloatingActionButton(
          child: Icon(Icons.add),
          onPressed: () async {
            try {
              dropInResponse = await FlutterAdyen.openDropIn(
                  paymentMethods: jsonEncode(examplePaymentMethods),  // the result of your payment methods call
                  baseUrl: 'https://yourdomain.com',
                  amount: '100', // amount in cents
                  returnUrl: 'http://asd.de', //required for iOS
                  publicKey: 'publickey',
                  merchantAccount: '',
                  clientKey: 'clientkey',
                  additionalData: {},
                  environment: 'TEST',
                  shopperReference: '');
              setState(() {
                _payment_result = dropInResponse?.name ?? 'Unknown';
              });
            } on AdyenException catch(e) {
              setState(() {
                _payment_result = e.error.name;
              });
            }
            // } on PlatformException catch (e) {
            //   if (e.code == 'PAYMENT_CANCELLED')
            //     dropInResponse = 'Payment Cancelled';
            //   else
            //     dropInResponse = 'Payment Error';
            // }

            // setState(() {
            //   _payment_result = dropInResponse;
            // });
          },
        ),
        appBar: AppBar(
          title: const Text('Flutter Adyen'),
        ),
        body: Center(
          child: Text('Payment Result: $_payment_result\n'),
        ),
      ),
    );
  }
}
